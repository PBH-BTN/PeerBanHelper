package com.ghostchu.peerbanhelper.banpipeline;

import com.ghostchu.peerbanhelper.DownloaderServer;
import com.ghostchu.peerbanhelper.DownloaderServerImpl;
import com.ghostchu.peerbanhelper.alert.AlertManager;
import com.ghostchu.peerbanhelper.banpipeline.data.CheckResultBatch;
import com.ghostchu.peerbanhelper.banpipeline.organ.DownloaderLoginOrgan;
import com.ghostchu.peerbanhelper.banpipeline.organ.DownloaderProviderOrgan;
import com.ghostchu.peerbanhelper.banpipeline.organ.PeersFetchOrgan;
import com.ghostchu.peerbanhelper.banpipeline.organ.RunCheckModuleOrgan;
import com.ghostchu.peerbanhelper.banpipeline.organ.RunMonitorModuleOrgan;
import com.ghostchu.peerbanhelper.banpipeline.organ.TorrentsFetchOrgan;
import com.ghostchu.peerbanhelper.banpipeline.organ.UpdateSnapshotOrgan;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.downloader.DownloaderManager;
import com.ghostchu.peerbanhelper.module.CheckResult;
import com.ghostchu.peerbanhelper.module.ModuleManager;
import com.ghostchu.peerbanhelper.util.Pair;
import com.ghostchu.peerbanhelper.util.WatchDog;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class DigestionSession {
    @Getter
    private final UUID sessionId = UUID.randomUUID();
    private final long sessionStartAt = System.currentTimeMillis();
    private final ExecutorService scheduleEnergy = Executors.newWorkStealingPool(Math.max(4, Runtime.getRuntime().availableProcessors() - 1));
    private final Executor digestEnergy = Executors.newWorkStealingPool(Math.max(4, Runtime.getRuntime().availableProcessors() - 1));
    private final DownloaderManager downloaderManager;
    private final DownloaderServer downloaderServer;
    private final ModuleManager moduleManager;
    private final List<BanOrgan<?, ?>> organs = new ArrayList<>();
    private final AlertManager alertManager;

    public DigestionSession(DownloaderManager downloaderManager, DownloaderServer downloaderServer, ModuleManager moduleManager, AlertManager alertManager) {
        this.downloaderManager = downloaderManager;
        this.downloaderServer = downloaderServer;
        this.moduleManager = moduleManager;
        this.alertManager = alertManager;
    }

    public Pair<Map<Downloader, List<DownloaderServerImpl.BanDetail>>, ProcessingStatistics> runBanWave(WatchDog banWaveWatchDog) {
        organs.clear();
        DownloaderProviderOrgan downloaderProviderOrgan = new DownloaderProviderOrgan(
                downloaderManager,
                scheduleEnergy,
                digestEnergy,
                null,
                null,
                60, TimeUnit.SECONDS
        );
        organs.add(downloaderProviderOrgan);
        DownloaderLoginOrgan downloaderLoginOrgan = new DownloaderLoginOrgan(
                scheduleEnergy,
                digestEnergy,
                downloaderProviderOrgan,
                null,
                60, TimeUnit.SECONDS
        );
        organs.add(downloaderLoginOrgan);
        TorrentsFetchOrgan torrentsFetchOrgan = new TorrentsFetchOrgan(
                scheduleEnergy,
                digestEnergy,
                downloaderLoginOrgan,
                null,
                60, TimeUnit.SECONDS
        );
        organs.add(torrentsFetchOrgan);
        PeersFetchOrgan peersFetchOrgan = new PeersFetchOrgan(
                scheduleEnergy,
                digestEnergy,
                torrentsFetchOrgan,
                null,
                60, TimeUnit.SECONDS
        );
        organs.add(peersFetchOrgan);
        UpdateSnapshotOrgan updateSnapshotOrgan = new UpdateSnapshotOrgan(
                scheduleEnergy,
                digestEnergy,
                peersFetchOrgan,
                null,
                60, TimeUnit.SECONDS,
                (DownloaderServerImpl) downloaderServer
        );
        organs.add(updateSnapshotOrgan);
        RunMonitorModuleOrgan runMonitorModuleOrgan = new RunMonitorModuleOrgan(
                scheduleEnergy,
                digestEnergy,
                updateSnapshotOrgan,
                null,
                60, TimeUnit.SECONDS,
                moduleManager
        );
        organs.add(runMonitorModuleOrgan);
        RunCheckModuleOrgan runCheckModuleOrgan = new RunCheckModuleOrgan(
                scheduleEnergy,
                digestEnergy,
                runMonitorModuleOrgan,
                null,
                60, TimeUnit.SECONDS,
                moduleManager,
                alertManager,
                (DownloaderServerImpl) downloaderServer
        );
        organs.add(runCheckModuleOrgan);
        runCheckModuleOrgan.endSession();
        return convertBanDetails(extractFromLastOrgan(runCheckModuleOrgan));
    }

    /**
     * 这是为了从 Organ outlet 输出的 CheckResultBatch 拼合成能够处理的形式
     * 需要用嵌套 Map，因为需要比对已存在的 Peer 的其它 CheckResult 结果的优先级
     *
     * @param lastOrgan 最后输出的 Organ
     * @return 中间产物
     */
    private Map<Downloader, Map<Torrent, Map<Peer, CheckResult>>> extractFromLastOrgan(BanOrgan<?, CheckResultBatch> lastOrgan) {
        Map<Downloader, Map<Torrent, Map<Peer, CheckResult>>> handled = new HashMap<>();
        try {
            CheckResultBatch lastRetrieve;
            do {
                lastRetrieve = lastOrgan.outlet.poll(10, TimeUnit.MILLISECONDS);
                if (lastRetrieve == null) {
                    for (BanOrgan<?, ?> organ : organs) {
                        log.debug("--------------");
                        log.debug("ORGAN: {}", organ.getClass().getSimpleName());
                        log.debug("Life Cycle Done: {}", organ.getStatus().name());
                        log.debug("Running Tasks: {}", organ.runningTasks.size());
                        log.debug("Outlet Waiting Retrieve: {}", organ.outlet.size());
                    }
                    continue;
                }
                var torrentMap = handled.getOrDefault(lastRetrieve.downloader(), Collections.synchronizedMap(new HashMap<>()));
                var peersMap = torrentMap.getOrDefault(lastRetrieve.torrent(), Collections.synchronizedMap(new HashMap<>()));
                var lastPeer = peersMap.getOrDefault(lastRetrieve.peer(), lastRetrieve.checkResult());
                // 现在检查状态，SKIP > BAN > NO_ACTION
                if (lastPeer.action().ordinal() > lastRetrieve.checkResult().action().ordinal()) { // 如果状态等级更高，则忽略避免覆写
                    continue;
                }
                if (lastPeer.action().ordinal() == lastRetrieve.checkResult().action().ordinal() &&
                        lastPeer.duration() > lastRetrieve.checkResult().duration()) { // 如果状态等级相同，则避免 banDuration 变短
                    continue;
                }
                // 其他情况允许覆盖
                // 检查结束
                peersMap.put(lastRetrieve.peer(), lastRetrieve.checkResult());
                torrentMap.put(lastRetrieve.torrent(), peersMap);
                handled.put(lastRetrieve.downloader(), torrentMap);
            } while (lastOrgan.getStatus() != OrganLifeCycleStatus.DONE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return handled;
    }


    /**
     * 为了节省那点 memory footpoint，这坨屎山就放这里了
     *
     * @param handled 上级处理的数据
     * @return 转换后的 BanDetails 集合
     */
    public Pair<Map<Downloader, List<DownloaderServerImpl.BanDetail>>, ProcessingStatistics> convertBanDetails(Map<Downloader, Map<Torrent, Map<Peer, CheckResult>>> handled) {
        // 下面这段代码只能写一次，因为再读就读不懂了
        // 总之是数据转换，以一种最节省 Heap 的方式进行
        // 实在看不懂就找 AI 问问吧
        // ~Ghost

        long downloaders = 0;
        long torrents = 0;
        long peers = 0;
        Map<Downloader, List<DownloaderServerImpl.BanDetail>> banDetails = new HashMap<>();
        var it = handled.entrySet().iterator();
        while (it.hasNext()) {
            var l2 = it.next();
            var downloader = l2.getKey();
            downloaders ++;
            var banDetailSection = banDetails.getOrDefault(downloader, new ArrayList<>());
            var l3It = l2.getValue().entrySet().iterator();
            while (l3It.hasNext()) {
                var l3 = l3It.next();
                var torrent = l3.getKey();
                torrents ++;
                var l4It = l3.getValue().entrySet().iterator();
                while (l4It.hasNext()) {
                    var l4 = l4It.next();
                    var peer = l4.getKey();
                    peers++;
                    var checkResult = l4.getValue();
                    banDetailSection.add(new DownloaderServerImpl.BanDetail(
                            torrent, peer, checkResult, checkResult.duration()
                    ));
                    l4It.remove();
                }
                l3It.remove();
            }
            banDetails.put(downloader, banDetailSection);
            it.remove();
        }
        return Pair.of(banDetails, new ProcessingStatistics(downloaders,torrents,peers));
    }

    public record ProcessingStatistics(
            long downloaders,
            long torrents,
            long peers
    ) {
    }
}
