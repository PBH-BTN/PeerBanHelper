package com.ghostchu.peerbanhelper.module.impl.rule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ghostchu.peerbanhelper.BanList;
import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.banpipeline.PipelineTask;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.databasent.service.PCBAddressService;
import com.ghostchu.peerbanhelper.databasent.service.PCBRangeService;
import com.ghostchu.peerbanhelper.databasent.table.PCBAddressEntity;
import com.ghostchu.peerbanhelper.databasent.table.PCBRangeEntity;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.downloader.DownloaderFeatureFlag;
import com.ghostchu.peerbanhelper.event.banwave.PeerUnbanEvent;
import com.ghostchu.peerbanhelper.module.AbstractRuleFeatureModule;
import com.ghostchu.peerbanhelper.module.CheckResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.*;
import com.ghostchu.peerbanhelper.util.backgroundtask.BackgroundTaskManager;
import com.ghostchu.peerbanhelper.util.backgroundtask.FunctionalBackgroundTask;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import com.ghostchu.peerbanhelper.wrapper.StructuredData;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import com.google.common.eventbus.Subscribe;
import inet.ipaddr.IPAddress;
import io.javalin.http.Context;
import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Component
@Slf4j
public final class ProgressCheatBlocker extends AbstractRuleFeatureModule implements Reloadable {
    private static final int CACHE_SIZE = 1024;
    private static final int PREFETCH_SIZE = 100;
    private final LinkedHashMap<CacheKeyPrefix, PCBRangeEntity> prefixCache = new LinkedHashMap<>(16, 0.75f, true);
    private final LinkedHashMap<CacheKeyAddr, PCBAddressEntity> addrCache = new LinkedHashMap<>(16, 0.75f, true);
    private long torrentMinimumSize;
    private boolean blockExcessiveClients;
    private double excessiveThreshold;
    private double maximumDifference;
    private double rewindMaximumDifference;
    private int ipv4PrefixLength;
    private int ipv6PrefixLength;
    @Autowired
    private JavalinWebContainer webContainer;
    private long banDuration;
    @Autowired
    private PCBRangeService pcbRangeDao;
    @Autowired
    private PCBAddressService pcbAddressDao;
    private long persistDuration;
    private long maxWaitDuration;
    private long fastPcbTestBlockingDuration;
    private double fastPcbTestPercentage;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private BackgroundTaskManager backgroundTaskManager;
    @Autowired
    private BanList banList;


    @Override
    public @NotNull String getName() {
        return "Progress Cheat Blocker";
    }

    @Override
    public @NotNull String getConfigName() {
        return "progress-cheat-blocker";
    }

    @Override
    public void onEnable() {
        reloadConfig();
        webContainer.routes()
                .get("/api/modules/" + getConfigName(), this::handleConfig, Role.USER_READ);
        registerScheduledTask(this::cleanDatabase, 0, 8, TimeUnit.HOURS);
        long interval = Math.max(1, getConfig().getLong("data-flush-interval", 180_000));
        registerScheduledTask(this::flushSafely, interval, interval, TimeUnit.MILLISECONDS);
        Main.getReloadManager().register(this);
        Main.getEventBus().register(this);
    }


    @Subscribe
    public synchronized void onPeerUnBan(PeerUnbanEvent event) {
        transactionTemplate.execute(_ -> {
            event.getUnbannedPeers().forEach((addr, meta) -> pcbAddressDao.deleteEntry(meta.getTorrent().getId(), addr.toInetAddress()));
            return null;
        });
        event.getUnbannedPeers().forEach((addr, meta) -> addrCache.keySet().removeIf(key ->
                key.torrentId().equals(meta.getTorrent().getId()) && key.peerAddressIp().equals(addr.toInetAddress())));
        log.debug("Deleted unbanned peers");
    }

    private void cleanDatabase() {
        backgroundTaskManager.addTaskAsync(new FunctionalBackgroundTask(
                new TranslationComponent(Lang.MODULE_PCB_BGTASK_DELETING_EXPIRED_DATA),
                (task, callback) -> {
                    try {
                        log.info(tlUI(Lang.MODULE_PCB_DELETING_EXPIRED_DATA));
                        var ofd = OffsetDateTime.now().minus(persistDuration, ChronoUnit.MILLIS);
                        long deleted = deleteExpired(ofd);
                        log.info(tlUI(Lang.MODULE_PCB_DELETED_EXPIRED_DATA, deleted));
                    } catch (Throwable e) {
                        log.error("Unable to remove expired data from database", e);
                        Sentry.captureException(e);
                        throw e;
                    }
                }
        )).join();
    }

    public void handleConfig(Context ctx) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("torrentMinimumSize", torrentMinimumSize);
        config.put("blockExcessiveClients", blockExcessiveClients);
        config.put("excessiveThreshold", excessiveThreshold);
        config.put("maximumDifference", maximumDifference);
        config.put("rewindMaximumDifference", rewindMaximumDifference);
        ctx.json(new StdResp(true, null, config));
    }

    @Override
    public boolean isThreadSafe() {
        return true;
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public ReloadResult reloadModule() throws Exception {
        reloadConfig();
        return Reloadable.super.reloadModule();
    }

    @Override
    public void onDisable() {
        Main.getEventBus().unregister(this);
        Main.getReloadManager().unregister(this);
        try {
            flushTracking();
        } catch (Exception e) {
            log.warn("Unable to close cache instance", e);
        }
    }


    private void reloadConfig() {
        this.banDuration = getConfig().getLong("ban-duration", 0);
        this.torrentMinimumSize = getConfig().getLong("minimum-size");
        this.blockExcessiveClients = getConfig().getBoolean("block-excessive-clients");
        this.excessiveThreshold = getConfig().getDouble("excessive-threshold");
        this.maximumDifference = getConfig().getDouble("maximum-difference");
        this.rewindMaximumDifference = getConfig().getDouble("rewind-maximum-difference");
        this.ipv4PrefixLength = getConfig().getInt("ipv4-prefix-length");
        this.ipv6PrefixLength = getConfig().getInt("ipv6-prefix-length");
        this.persistDuration = getConfig().getLong("persist-duration");
        this.maxWaitDuration = getConfig().getLong("max-wait-duration");
        this.fastPcbTestPercentage = getConfig().getDouble("fast-pcb-test-percentage");
        this.fastPcbTestBlockingDuration = getConfig().getLong("fast-pcb-test-block-duration");
        getCache().invalidateAll();
    }


    @Override
    public synchronized @NotNull CheckResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull Downloader downloader, @NotNull PipelineTask<?> task) {
        if (isHandShaking(peer)) {
            return handshaking();
        }
        // 处理 IPV6
        IPAddress peerIp = peer.getPeerAddress().getAddress();
        String peerPrefixString = prefixOf(peerIp);
        var prefix = String.format("%s,%s,%s,%s,%s", downloader.getId(), torrent.getId(), peerPrefixString, peerIp.toInetAddress(), peer.getPeerAddress().getPort());
        task.setComment(true, "Loading data from DB for: " + prefix);
        var pair = loadFromDatabase(downloader.getId(), torrent.getId(), peerPrefixString, peerIp.toInetAddress(), peer.getPeerAddress().getPort());
        task.setComment(false, "Executing and determining whether to ban the peer based on loaded data and current report.");
        PCBRangeEntity rangeEntity = pair.getLeft();
        PCBAddressEntity addressEntity = pair.getRight();
        long computedUploadedIncremental; // 上传增量
        if (peer.getUploaded() < addressEntity.getLastReportUploaded()) {
            computedUploadedIncremental = peer.getUploaded();
        } else {
            computedUploadedIncremental = peer.getUploaded() - addressEntity.getLastReportUploaded();
        }
        // 累加 IP、IP段 上传增量
        addressEntity.setTrackingUploadedIncreaseTotal(addressEntity.getTrackingUploadedIncreaseTotal() + computedUploadedIncremental);
        rangeEntity.setTrackingUploadedIncreaseTotal(rangeEntity.getTrackingUploadedIncreaseTotal() + computedUploadedIncremental);
        // 获取真实已上传量（下载器报告、IP增量总计，段增量总计，三者取最大）
        final long computedUploaded = Math.max(peer.getUploaded(), Math.max(addressEntity.getTrackingUploadedIncreaseTotal(), rangeEntity.getTrackingUploadedIncreaseTotal()));
        try {
            final long torrentSize = torrent.getSize();
            final long completedSize = torrent.getCompletedSize();
            final long computedCompletedSize = Math.max(completedSize, Math.max(rangeEntity.getLastTorrentCompletedSize(), addressEntity.getLastTorrentCompletedSize()));
            // 过滤
            if (torrentSize <= 0) {
                return pass();
            }
            if (!isUploadingToPeer(peer)) {
                return pass();
            }
            var structuredData = StructuredData.create()
                    .add("torrentSize", torrentSize)
                    .add("completedSize", completedSize)
                    .add("computedCompletedSize", computedCompletedSize)
                    .add("peerReportUploaded", peer.getUploaded())
                    .add("peerLastReportUploaded", addressEntity.getLastReportUploaded())
                    .add("prefixLastReportUploaded", rangeEntity.getLastReportUploaded())
                    .add("peerTrackingUploadedIncreaseTotal", addressEntity.getTrackingUploadedIncreaseTotal())
                    .add("prefixTrackingUploadedIncreaseTotal", rangeEntity.getTrackingUploadedIncreaseTotal())
                    .add("actualUploaded", computedUploaded)
                    .add("uploadedIncremental", computedUploadedIncremental)
                    .add("fileTooSmall", fileTooSmall(torrentSize))
                    .add("fastPcbTestPercentage", fastPcbTestPercentage);
            // 快速 PCB 测试
            {
                task.setComment(false, "Run fastPcbTest for " + prefix);
                CheckResult result = fastPcbTest(addressEntity, rangeEntity, computedUploaded, torrentSize, structuredData, downloader);
                if (result != null) return result;
            }
            // 计算进度信息
            final double computedProgress = (double) computedUploaded / torrentSize; // 实际进度
            final double clientReportedProgress = peer.getProgress(); // 客户端汇报进度
            structuredData.add("computedProgress", computedProgress);
            structuredData.add("clientReportedProgress", clientReportedProgress);
            // 过量下载检查
            // actualUploaded = -1 代表客户端不支持统计此 Peer 总上传量
            {
                task.setComment(false, "Run excessiveClientTest for " + prefix);
                CheckResult result = excessiveClient(computedUploaded, torrentSize, structuredData, rangeEntity, addressEntity, completedSize, computedCompletedSize);
                if (result != null) return result;
            }
            // 如果客户端报告自己进度更多，则跳过检查
            if (computedProgress <= clientReportedProgress) {
                return pass();
            }
            // 计算进度差异
            // isUploadingToPeer 是为了确认下载器再给对方上传数据，因为对方使用 “超级做种” 时汇报的进度可能并不准确
            {
                task.setComment(false, "Run differenceTest for " + prefix);
                CheckResult result = differenceTest(rangeEntity, addressEntity, clientReportedProgress, computedProgress, structuredData, torrentSize, peer);
                if (result != null) return result;
            }
            {
                task.setComment(false, "Run progressRewindTest for " + prefix);
                CheckResult result = progressRewind(peer, structuredData, rangeEntity, addressEntity, clientReportedProgress, computedProgress, torrentSize);
                if (result != null) return result;
            }
            //return new CheckResult(getClass(), PeerAction.NO_ACTION, "N/A", String.format(Lang.MODULE_PCB_PEER_BAN_INCORRECT_PROGRESS, percent(clientProgress), percent(actualProgress), percent(difference)));
            return pass();
        } finally {
            // 无论如何都写入缓存，同步更改
            addressEntity.setLastReportUploaded(peer.getUploaded());
            if (peer.getProgress() != 0.0d) { // 不要保存 0.0d 的，避免覆盖库中数据，导致触发 PCB 立刻封禁
                addressEntity.setLastReportProgress(peer.getProgress());
                rangeEntity.setLastReportProgress(peer.getProgress());
            }
            addressEntity.setLastTorrentCompletedSize(Math.max(torrent.getCompletedSize(), addressEntity.getLastTorrentCompletedSize()));
            addressEntity.setLastTimeSeen(OffsetDateTime.now());
            rangeEntity.setLastReportUploaded(peer.getUploaded());
            rangeEntity.setLastTorrentCompletedSize(Math.max(torrent.getCompletedSize(), rangeEntity.getLastTorrentCompletedSize()));
            rangeEntity.setLastTimeSeen(OffsetDateTime.now());
        }
    }

    private @Nullable CheckResult differenceTest(PCBRangeEntity rangeEntity, PCBAddressEntity addressEntity, double clientReportedProgress, double computedProgress, StructuredData<String, Object> structuredData, long torrentSize, Peer peer) {
        double difference = Math.abs(computedProgress - clientReportedProgress);
        structuredData.add("difference", difference);
        if (difference > maximumDifference && !fileTooSmall(torrentSize) && isUploadingToPeer(peer)) {
            if (!isBanDelayWindowScheduled(rangeEntity, addressEntity)) {
                scheduleBanDelayWindow(rangeEntity, addressEntity);
                return null;
            }
            if (isBanDelayWindowExpired(rangeEntity, addressEntity)) {
                rangeEntity.setProgressDifferenceCounter(rangeEntity.getProgressDifferenceCounter() + 1);
                addressEntity.setProgressDifferenceCounter(addressEntity.getProgressDifferenceCounter() + 1);
                resetBanDelayWindow(rangeEntity, addressEntity);
                return new CheckResult(getClass(), PeerAction.BAN, banDuration, new TranslationComponent(Lang.PCB_RULE_REACHED_MAX_DIFFERENCE),
                        new TranslationComponent(Lang.MODULE_PCB_PEER_BAN_INCORRECT_PROGRESS,
                                percent(clientReportedProgress),
                                percent(computedProgress),
                                percent(difference)),
                        structuredData.add("type", "deSyncDifference"));
            }
        }
        return null;
    }


    private @Nullable CheckResult progressRewind(@NotNull Peer peer, @NotNull StructuredData<String, Object> structuredData, @NotNull PCBRangeEntity rangeEntity, @NotNull PCBAddressEntity addressEntity, double clientReportedProgress, double computedProgress, long torrentSize) {
        if (rewindMaximumDifference > 0 && !fileTooSmall(torrentSize)) {
            double lastReportProgress = Math.max(addressEntity.getLastReportProgress(), rangeEntity.getLastReportProgress());
            double rewind = lastReportProgress - peer.getProgress();
            structuredData.add("peerLastReportProgress", addressEntity.getLastReportProgress());
            structuredData.add("rangeLastRecordProgress", rangeEntity.getLastReportProgress());
            structuredData.add("lastReportProgress", lastReportProgress);
            structuredData.add("rewind", rewind);
            structuredData.add("rewindMaximumDifference", rewindMaximumDifference);
            log.debug("Debug: Peer {} rewind check: lastReportProgress={}, currentProgress={}, rewind={}, rewindMaximumDifference={}",
                    peer.getPeerAddress(),
                    percent(lastReportProgress),
                    percent(peer.getProgress()),
                    percent(rewind),
                    percent(rewindMaximumDifference)
            );

            if ((rewind > rewindMaximumDifference && isUploadingToPeer(peer))) {
                if (peer.getProgress() > 0.0d || isBanDelayWindowExpired(rangeEntity, addressEntity)) { // 满足等待时间或者 Peer 进度大于 0% (Peer 已更新 BIT_FIELD)
                    addressEntity.setRewindCounter(addressEntity.getRewindCounter() + 1);
                    rangeEntity.setRewindCounter(rangeEntity.getRewindCounter() + 1);
                    resetBanDelayWindow(rangeEntity, addressEntity);
                    return new CheckResult(getClass(), PeerAction.BAN, banDuration, new TranslationComponent(Lang.PCB_RULE_PROGRESS_REWIND),
                            new TranslationComponent(Lang.MODULE_PCB_PEER_BAN_REWIND,
                                    percent(clientReportedProgress),
                                    percent(computedProgress),
                                    percent(lastReportProgress),
                                    percent(rewind),
                                    percent(rewindMaximumDifference)), structuredData.add("type", "rewindProgress"));
                } else {
                    if (!isBanDelayWindowScheduled(rangeEntity, addressEntity)) {
                        scheduleBanDelayWindow(rangeEntity, addressEntity);
                    }
                }
            }
        }

        return null;
    }

    private @Nullable CheckResult excessiveClient(long computedUploaded, long torrentSize, StructuredData<String, Object> structuredData, PCBRangeEntity rangeEntity, PCBAddressEntity addressEntity, long completedSize, long computedCompletedSize) {
        if (computedUploaded != -1 && blockExcessiveClients) {
            if (computedUploaded > torrentSize) {
                // 下载量超过种子大小，检查
                long maxAllowedExcessiveThreshold = (long) (Math.max(torrentSize, torrentMinimumSize) * excessiveThreshold);
                structuredData.add("maxAllowedExcessiveThreshold", maxAllowedExcessiveThreshold);
                if (computedUploaded > maxAllowedExcessiveThreshold) {
                    resetBanDelayWindow(rangeEntity, addressEntity);
                    return new CheckResult(getClass(), PeerAction.BAN, banDuration, new TranslationComponent(Lang.PCB_RULE_REACHED_MAX_ALLOWED_EXCESSIVE_THRESHOLD),
                            new TranslationComponent(Lang.MODULE_PCB_EXCESSIVE_DOWNLOAD,
                                    torrentSize,
                                    computedUploaded,
                                    maxAllowedExcessiveThreshold),
                            structuredData.add("type", "excessiveMaxDownloadThreshold"));
                }
            } else if (ExternalSwitch.parse("pbh.pcb.disable-completed-excessive") == null && completedSize > 0 && computedUploaded > completedSize) {
                // 下载量超过任务大小，检查
                long maxAllowedExcessiveThreshold = (long) (Math.max(computedCompletedSize, torrentMinimumSize) * excessiveThreshold);
                structuredData.add("maxAllowedExcessiveThreshold", maxAllowedExcessiveThreshold);
                if (computedUploaded > maxAllowedExcessiveThreshold) {
                    resetBanDelayWindow(rangeEntity, addressEntity);
                    return new CheckResult(getClass(), PeerAction.BAN, banDuration, new TranslationComponent(Lang.PCB_RULE_REACHED_MAX_ALLOWED_EXCESSIVE_THRESHOLD),
                            new TranslationComponent(Lang.MODULE_PCB_EXCESSIVE_DOWNLOAD_INCOMPLETE,
                                    torrentSize,
                                    completedSize,
                                    computedUploaded,
                                    maxAllowedExcessiveThreshold),
                            structuredData.add("type", "excessiveMaxDownloadThresholdForIncompleteTask"));
                }
            }
        }
        return null;
    }

    private @Nullable CheckResult fastPcbTest(PCBAddressEntity addressEntity, PCBRangeEntity rangeEntity, long computedUploaded, long torrentSize, StructuredData<String, Object> structuredData, Downloader downloader) {
        if (fastPcbTestPercentage > 0 && !fileTooSmall(torrentSize) && downloader.getFeatureFlags().contains(DownloaderFeatureFlag.UNBAN_IP)) {
            // 只在 <= 0（也就是从未测试过）的情况下对其进行测试
            if (addressEntity.getFastPcbTestExecuteAt().isEqual(TimeUtil.zeroOffsetDateTime) || rangeEntity.getFastPcbTestExecuteAt().isEqual(TimeUtil.zeroOffsetDateTime)) {
                // 如果上传量大于设置的比率，我们主动断开一次连接，封禁 Peer 一段时间，并尽快解除封禁
                if (computedUploaded >= (fastPcbTestPercentage * torrentSize)) {
                    addressEntity.setFastPcbTestExecuteAt(OffsetDateTime.now());
                    rangeEntity.setFastPcbTestExecuteAt(OffsetDateTime.now());
                    return new CheckResult(getClass(), PeerAction.BAN_FOR_DISCONNECT, fastPcbTestBlockingDuration,
                            new TranslationComponent(Lang.PCB_RULE_PEER_PROGRESS_CHEAT_TESTING),
                            new TranslationComponent(Lang.PCB_DESCRIPTION_PEER_PROGRESS_CHEAT_TESTING),
                            structuredData.add("type", "fastPcbTest")
                    );
                }
            }
        }
        return null;
    }

    private void scheduleBanDelayWindow(@Nullable PCBRangeEntity rangeEntity, @Nullable PCBAddressEntity addressEntity) {
        if (rangeEntity != null && rangeEntity.getBanDelayWindowEndAt().toInstant().toEpochMilli() <= 0) {
            rangeEntity.setBanDelayWindowEndAt(OffsetDateTime.now().plus(this.maxWaitDuration, ChronoUnit.MILLIS));
        }
        if (addressEntity != null && addressEntity.getBanDelayWindowEndAt().toInstant().toEpochMilli() <= 0) {
            addressEntity.setBanDelayWindowEndAt(OffsetDateTime.now().plus(this.maxWaitDuration, ChronoUnit.MILLIS));
        }
    }

    private boolean isBanDelayWindowScheduled(@Nullable PCBRangeEntity rangeEntity, @Nullable PCBAddressEntity addressEntity) {
        return rangeEntity != null && rangeEntity.getBanDelayWindowEndAt().toInstant().toEpochMilli() > 0
                || addressEntity != null && addressEntity.getBanDelayWindowEndAt().toInstant().toEpochMilli() > 0;
    }

    private boolean isBanDelayWindowExpired(@Nullable PCBRangeEntity rangeEntity, @Nullable PCBAddressEntity addressEntity) {
        return (rangeEntity != null && rangeEntity.getBanDelayWindowEndAt().toInstant().toEpochMilli() > 0
                && rangeEntity.getBanDelayWindowEndAt().isBefore(OffsetDateTime.now()))
                || (addressEntity != null && addressEntity.getBanDelayWindowEndAt().toInstant().toEpochMilli() > 0
                && addressEntity.getBanDelayWindowEndAt().isBefore(OffsetDateTime.now()));
    }

    private void resetBanDelayWindow(@Nullable PCBRangeEntity rangeEntity, @Nullable PCBAddressEntity addressEntity) {
        if (rangeEntity != null && rangeEntity.getBanDelayWindowEndAt().toInstant().toEpochMilli() > 0) {
            rangeEntity.setBanDelayWindowEndAt(TimeUtil.zeroOffsetDateTime);
        }
        if (addressEntity != null && addressEntity.getBanDelayWindowEndAt().toInstant().toEpochMilli() > 0) {
            addressEntity.setBanDelayWindowEndAt(TimeUtil.zeroOffsetDateTime);
        }
    }

    private boolean isUploadingToPeer(Peer peer) {
        return peer.getUploadSpeed() > 0 || peer.getUploaded() > 0;
    }

    private boolean fileTooSmall(long torrentSize) {
        return torrentSize < torrentMinimumSize;
    }

    private String prefixOf(IPAddress address) {
        return address.toPrefixBlock(address.isIPv4() ? ipv4PrefixLength : ipv6PrefixLength).toString();
    }

    @Override
    public synchronized void preparePeers(Downloader downloader, Torrent torrent, List<? extends Peer> peers) {
        var missingRanges = new LinkedHashSet<String>();
        var missingAddresses = new LinkedHashSet<CacheKeyAddr>();
        int rangeLimit = Math.min(PREFETCH_SIZE, CACHE_SIZE - prefixCache.size());
        int addressLimit = Math.min(PREFETCH_SIZE, CACHE_SIZE - addrCache.size());
        for (Peer peer : peers) {
            if (missingRanges.size() >= rangeLimit && missingAddresses.size() >= addressLimit) break;
            if (isHandShaking(peer)) continue;
            var address = peer.getPeerAddress();
            var ip = address.getAddress();
            if (missingRanges.size() < rangeLimit) {
                var prefix = prefixOf(ip);
                if (!prefixCache.containsKey(new CacheKeyPrefix(downloader.getId(), torrent.getId(), prefix))) {
                    missingRanges.add(prefix);
                }
            }
            if (missingAddresses.size() < addressLimit) {
                var key = new CacheKeyAddr(downloader.getId(), torrent.getId(), ip.toInetAddress(), address.getPort());
                if (!addrCache.containsKey(key)) {
                    missingAddresses.add(key);
                }
            }
        }

        if (!missingRanges.isEmpty()) {
            var found = pcbRangeDao.list(new LambdaQueryWrapper<PCBRangeEntity>()
                    .eq(PCBRangeEntity::getDownloader, downloader.getId())
                    .eq(PCBRangeEntity::getTorrentId, torrent.getId())
                    .in(PCBRangeEntity::getRange, missingRanges));
            for (var entity : found) {
                prefixCache.put(new CacheKeyPrefix(downloader.getId(), torrent.getId(), entity.getRange()), entity);
                missingRanges.remove(entity.getRange());
            }
            for (var prefix : missingRanges) {
                var key = new CacheKeyPrefix(downloader.getId(), torrent.getId(), prefix);
                prefixCache.put(key, newRange(key));
            }
        }
        if (!missingAddresses.isEmpty()) {
            var found = pcbAddressDao.list(new LambdaQueryWrapper<PCBAddressEntity>()
                    .eq(PCBAddressEntity::getDownloader, downloader.getId())
                    .eq(PCBAddressEntity::getTorrentId, torrent.getId())
                    .and(group -> missingAddresses.forEach(key -> group.or(match -> match
                            .eq(PCBAddressEntity::getIp, key.peerAddressIp())
                            .eq(PCBAddressEntity::getPort, key.port())))));
            for (var entity : found) {
                var key = new CacheKeyAddr(downloader.getId(), torrent.getId(), entity.getIp(), entity.getPort());
                addrCache.put(key, entity);
                missingAddresses.remove(key);
            }
            for (var key : missingAddresses) {
                addrCache.put(key, newAddress(key));
            }
        }
    }

    @NotNull
    private Pair<PCBRangeEntity, PCBAddressEntity> loadFromDatabase(String downloader, String torrentId, String peerAddressPrefix, InetAddress peerAddressIp, int port) {
        var prefixKey = new CacheKeyPrefix(downloader, torrentId, peerAddressPrefix);
        var addressKey = new CacheKeyAddr(downloader, torrentId, peerAddressIp, port);
        var prefix = prefixCache.get(prefixKey);
        var address = addrCache.get(addressKey);
        if ((prefix == null && prefixCache.size() >= CACHE_SIZE)
                || (address == null && addrCache.size() >= CACHE_SIZE)) {
            flushTracking();
            if (prefix == null && prefixCache.size() >= CACHE_SIZE) {
                for (int count = 0; count < PREFETCH_SIZE; count++) prefixCache.pollFirstEntry();
            }
            if (address == null && addrCache.size() >= CACHE_SIZE) {
                for (int count = 0; count < PREFETCH_SIZE; count++) addrCache.pollFirstEntry();
            }
        }
        if (prefix == null) {
            prefix = pcbRangeDao.fetchFromDatabase(torrentId, peerAddressPrefix, downloader);
            if (prefix == null) prefix = newRange(prefixKey);
            prefixCache.put(prefixKey, prefix);
        }
        if (address == null) {
            address = pcbAddressDao.fetchFromDatabase(torrentId, peerAddressIp, port, downloader);
            if (address == null) address = newAddress(addressKey);
            addrCache.put(addressKey, address);
        }
        return Pair.of(prefix, address);
    }

    private PCBRangeEntity newRange(CacheKeyPrefix key) {
        var now = OffsetDateTime.now();
        return new PCBRangeEntity(null, key.peerAddressPrefix(), key.torrentId(), 0, 0, 0, 0, 0, now, now,
                key.downloader(), TimeUtil.zeroOffsetDateTime, TimeUtil.zeroOffsetDateTime, 0);
    }

    private PCBAddressEntity newAddress(CacheKeyAddr key) {
        var now = OffsetDateTime.now();
        return new PCBAddressEntity(null, key.peerAddressIp(), key.port(), key.torrentId(), 0, 0, 0, 0, 0,
                now, now, key.downloader(), TimeUtil.zeroOffsetDateTime, TimeUtil.zeroOffsetDateTime, 0);
    }

    private void flushSafely() {
        try {
            flushTracking();
        } catch (RuntimeException e) {
            log.warn("Unable to flush progress tracking state", e);
        }
    }

    private synchronized void flushTracking() {
        var ranges = prefixCache.values().stream().filter(PCBRangeEntity::isDirty).toList();
        var addresses = addrCache.values().stream().filter(PCBAddressEntity::isDirty).toList();
        if (ranges.isEmpty() && addresses.isEmpty()) return;
        try {
            transactionTemplate.executeWithoutResult(status -> {
                ranges.forEach(pcbRangeDao::upsert);
                addresses.forEach(pcbAddressDao::upsert);
            });
        } catch (RuntimeException e) {
            ranges.forEach(entity -> entity.setDirty(true));
            addresses.forEach(entity -> entity.setDirty(true));
            throw e;
        }
    }

    private synchronized long deleteExpired(OffsetDateTime cutoff) {
        flushTracking();
        long deleted = pcbRangeDao.cleanupDatabase(cutoff) + pcbAddressDao.cleanupDatabase(cutoff);
        prefixCache.values().removeIf(entity -> entity.getLastTimeSeen().isBefore(cutoff));
        addrCache.values().removeIf(entity -> entity.getLastTimeSeen().isBefore(cutoff));
        return deleted;
    }

    private String percent(double value) {
        return MsgUtil.getPercentageFormatter().format(value);
    }

    record CacheKeyPrefix(String downloader, String torrentId, String peerAddressPrefix) {
    }

    record CacheKeyAddr(String downloader, String torrentId, InetAddress peerAddressIp, int port) {
    }
}


