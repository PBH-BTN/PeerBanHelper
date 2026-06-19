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
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.Pair;
import com.ghostchu.peerbanhelper.util.WatchDog;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

/**
 * Peers 处理会话，每次处理 BanWave 将打开一个新的会话，用于安全且正确的处理 Peers 获取、检查、封禁流程
 */
@Slf4j
public class DigestionSession {
    @Getter
    private final UUID sessionId = UUID.randomUUID();
    @Getter
    private final long sessionStartAt = System.currentTimeMillis();
    /*
    调度线程池
     */
    private final ExecutorService scheduleEnergy = Executors.newWorkStealingPool(Math.max(4, Runtime.getRuntime().availableProcessors() - 1));
    /*
    执行线程池
     */
    private final Executor digestEnergy = Executors.newWorkStealingPool(Math.max(4, Runtime.getRuntime().availableProcessors() - 1));
    private final DownloaderManager downloaderManager;
    private final DownloaderServer downloaderServer;
    private final ModuleManager moduleManager;
    /*
    管道处理节点
     */
    private final List<BanOrgan<?, ?>> organs = new ArrayList<>();
    private final AlertManager alertManager;

    public DigestionSession(DownloaderManager downloaderManager, DownloaderServer downloaderServer, ModuleManager moduleManager, AlertManager alertManager) {
        this.downloaderManager = downloaderManager;
        this.downloaderServer = downloaderServer;
        this.moduleManager = moduleManager;
        this.alertManager = alertManager;
    }

    /**
     * 执行 BanWave 流程
     *
     * @param banWaveWatchDog 监控 BanWave 的 WatchDog
     * @return BanWave 处理结果，包括封禁详情和处理统计信息
     */
    public Pair<Map<Downloader, List<DownloaderServerImpl.BanDetail>>, ProcessingStatistics> runBanWave(WatchDog banWaveWatchDog) {
        banWaveWatchDog.setLastOperation("Initializing runBanWave", false);
        organs.clear();
        // 下载器提供器
        DownloaderProviderOrgan downloaderProviderOrgan = new DownloaderProviderOrgan(
                downloaderManager,
                scheduleEnergy,
                digestEnergy,
                null,
                null,
                60, TimeUnit.SECONDS
        );
        organs.add(downloaderProviderOrgan);
        // 下载器会话登录器
        DownloaderLoginOrgan downloaderLoginOrgan = new DownloaderLoginOrgan(
                scheduleEnergy,
                digestEnergy,
                downloaderProviderOrgan,
                null,
                60, TimeUnit.SECONDS
        );
        organs.add(downloaderLoginOrgan);
        // 种子数据获取器
        TorrentsFetchOrgan torrentsFetchOrgan = new TorrentsFetchOrgan(
                scheduleEnergy,
                digestEnergy,
                downloaderLoginOrgan,
                null,
                60, TimeUnit.SECONDS
        );
        organs.add(torrentsFetchOrgan);
        // Peers 获取器
        PeersFetchOrgan peersFetchOrgan = new PeersFetchOrgan(
                scheduleEnergy,
                digestEnergy,
                torrentsFetchOrgan,
                null,
                60, TimeUnit.SECONDS
        );
        organs.add(peersFetchOrgan);
        // 内存快照更新器
        UpdateSnapshotOrgan updateSnapshotOrgan = new UpdateSnapshotOrgan(
                scheduleEnergy,
                digestEnergy,
                peersFetchOrgan,
                null,
                60, TimeUnit.SECONDS,
                (DownloaderServerImpl) downloaderServer
        );
        organs.add(updateSnapshotOrgan);
        // 观察者回调器
        RunMonitorModuleOrgan runMonitorModuleOrgan = new RunMonitorModuleOrgan(
                scheduleEnergy,
                digestEnergy,
                updateSnapshotOrgan,
                null,
                60, TimeUnit.SECONDS,
                moduleManager
        );
        organs.add(runMonitorModuleOrgan);
        // 规则和反吸血模块执行器
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
        // 等待数据并完成数据转换操作
        banWaveWatchDog.feed();
        var returns = convertBanDetails(extractFromLastOrgan(runCheckModuleOrgan, banWaveWatchDog));
        // 调用 Tail Organ 的 endSession 方法，其调用会随着链式递归调用到 Head Organ 的 endSession 方法，完成会话结束通知
        runCheckModuleOrgan.endSession();
        return returns;
    }

    /**
     * 这是为了从 Organ outlet 输出的 CheckResultBatch 拼合成能够处理的形式
     * 需要用嵌套 Map，因为需要比对已存在的 Peer 的其它 CheckResult 结果的优先级
     *
     * @param lastOrgan 最后输出的 Organ
     * @return 中间产物
     */
    private Map<Downloader, Map<Torrent, Map<Peer, CheckResult>>> extractFromLastOrgan(BanOrgan<?, CheckResultBatch> lastOrgan, WatchDog banWaveWatchDog) {
        boolean warningTriggered = false;
        Map<Downloader, Map<Torrent, Map<Peer, CheckResult>>> handled = new HashMap<>();
        try {
            CheckResultBatch lastRetrieve;
            do {
                lastRetrieve = lastOrgan.outlet.poll(10, TimeUnit.MILLISECONDS);
                if (lastRetrieve == null) {
                    // 这里插入 WatchDog，如果即将触发 WatchDog 则打印等待日志
                    if (System.currentTimeMillis() - banWaveWatchDog.getLastFeedAt().get() > (banWaveWatchDog.getTimeout() - (1000 * 60)) && !warningTriggered) {
                        StringJoiner joiner = new StringJoiner("\n");
                        for (BanOrgan<?, ?> organ : organs) {
                            joiner.add("--------------");
                            joiner.add("ORGAN:" + organ.getClass().getName());
                            joiner.add("Life Cycle Done: " + organ.getStatus().name());
                            joiner.add("Loop Running: " + organ.loopRunning.get());
                            if (organ.in != null) {
                                joiner.add("Upstream In: " + organ.in.getClass().getName());
                            } else {
                                joiner.add("Upstream In: null");
                            }
                            joiner.add("Running Tasks: " + organ.runningTasks.size());
                            organ.runningTasks.forEach(future -> joiner.add("  - " + Objects.requireNonNullElse(future, "null")));
                            joiner.add("Outlet Waiting Retrieve: " + organ.outlet.size());
                            organ.outlet.forEach(item -> joiner.add("  - " + Objects.requireNonNullElse(item, "null")));
                        }
                        joiner.add("-------------- (end)");
                        log.warn(tlUI(Lang.DIGESTION_SYSTEM_WATCH_EARLY_WARNING, joiner.toString()));
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
            downloaders++;
            var banDetailSection = banDetails.getOrDefault(downloader, new ArrayList<>());
            var l3It = l2.getValue().entrySet().iterator();
            while (l3It.hasNext()) {
                var l3 = l3It.next();
                var torrent = l3.getKey();
                torrents++;
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
        return Pair.of(banDetails, new ProcessingStatistics(downloaders, torrents, peers));
    }

    public record ProcessingStatistics(
            long downloaders,
            long torrents,
            long peers
    ) {
    }
}
