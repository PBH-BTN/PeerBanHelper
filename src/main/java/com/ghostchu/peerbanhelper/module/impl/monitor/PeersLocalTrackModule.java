package com.ghostchu.peerbanhelper.module.impl.monitor;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.database.dao.impl.tmp.TrackedPeersDao;
import com.ghostchu.peerbanhelper.database.table.tmp.TrackedPeerEntity;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.MonitorFeatureModule;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.context.IgnoreScan;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.ExecutorService;

@Slf4j
@Component
@IgnoreScan
public class PeersLocalTrackModule extends AbstractFeatureModule implements MonitorFeatureModule {
    @Autowired
    private TrackedPeersDao trackedPeersDao;

    /**
     * 如果返回 false，则不初始化任何配置文件相关对象
     *
     * @return 是否支持使用配置文件进行配置
     */
    @Override
    public boolean isConfigurable() {
        return true;
    }

    /**
     * 获取功能模块的人类可读名称
     *
     * @return 模块可读名称
     */
    @Override
    public @NotNull String getName() {
        return "Peers Local Track Module";
    }

    /**
     * 获取功能模块的内部配置键名
     *
     * @return 配置键名
     */
    @Override
    public @NotNull String getConfigName() {
        return "peers-local-track";
    }

    /**
     * 功能模块启用回调
     */
    @Override
    public void onEnable() {
        Main.getEventBus().register(this);
    }

    /**
     * 功能模块禁用回调
     */
    @Override
    public void onDisable() {
        Main.getEventBus().unregister(this);
    }

    @Override
    public void onTorrentPeersRetrieved(@NotNull Downloader downloader, @NotNull Torrent torrent, @NotNull List<Peer> peers, @NotNull ExecutorService ruleExecuteExecutor) {
        try {
            trackedPeersDao.callBatchTasks(() -> {
                for (Peer peer : peers) {
                    trackedPeersDao.upsert(new TrackedPeerEntity(
                            null,
                            peer.getPeerAddress().getAddress().toNormalizedString(),
                            peer.getPeerAddress().getPort(),
                            torrent.getHash(),
                            downloader.getName(),
                            peer.getPeerId(),
                            peer.getClientName(),
                            peer.getUploaded(),
                            -1L,
                            peer.getDownloaded(),
                            -1L,
                            peer.getFlags() == null ? "" : peer.getFlags().getLtStdString(),
                            new Timestamp(System.currentTimeMillis()),
                            new Timestamp(System.currentTimeMillis())
                    ));
                }
                return null;
            });
        } catch (SQLException e) {
            log.error("Unable update tracked peers in SQLite temporary table", e);
        }

    }
}
