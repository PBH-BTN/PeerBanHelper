package com.ghostchu.peerbanhelper.module.impl.monitor;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.database.dao.impl.tmp.TrackedSwarmDao;
import com.ghostchu.peerbanhelper.database.table.tmp.TrackedSwarmEntity;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.MonitorFeatureModule;
import com.ghostchu.peerbanhelper.util.query.Orderable;
import com.ghostchu.peerbanhelper.util.query.Pageable;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component

public final class SwarmTrackingModule extends AbstractFeatureModule implements MonitorFeatureModule {
    @Autowired
    private TrackedSwarmDao trackedSwarmDao;
    @Autowired
    private JavalinWebContainer javalinWebContainer;

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
        return "Swarm Tracking Module";
    }

    /**
     * 获取功能模块的内部配置键名
     *
     * @return 配置键名
     */
    @Override
    public @NotNull String getConfigName() {
        return "swarm-tracking";
    }

    /**
     * 功能模块启用回调
     */
    @Override
    public void onEnable() {
        Main.getEventBus().register(this);
        javalinWebContainer.javalin()
                .get("/api/modules/" + getConfigName(), this::handleWebAPI, Role.USER_READ);
        javalinWebContainer.javalin()
                .get("/api/modules/" + getConfigName() + "/details", this::handleDetails, Role.USER_READ);
    }

    private void handleDetails(@NotNull Context context) {
        Pageable pageable = new Pageable(context);
        try {
            var page = trackedSwarmDao.queryByPaging(new Orderable(Map.of(), context)
                    .apply(trackedSwarmDao.queryBuilder()), pageable);
            context.json(new StdResp(true, null, page));
        } catch (SQLException e) {
            log.error("Unable to retrieve tracked swarm data", e);
            context.json(new StdResp(false, "Unable to retrieve tracked swarm data", null));
        }
    }

    private void handleWebAPI(@NotNull Context context) {
        Map<String, Object> response = new HashMap<>();
        try {
            response.put("trackedSwarmSize", trackedSwarmDao.countOf());
            context.json(response);
        } catch (SQLException e) {
            log.error("Unable to retrieve tracked swarm data", e);
            context.status(500).json(new StdResp(false, "Unable to retrieve tracked swarm data", null));
            return;
        }
    }

    /**
     * 功能模块禁用回调
     */
    @Override
    public void onDisable() {
        Main.getEventBus().unregister(this);
    }

    @Override
    public void onTorrentPeersRetrieved(@NotNull Downloader downloader, @NotNull Torrent torrent, @NotNull List<Peer> peers) {
        try {
            trackedSwarmDao.callBatchTasks(() -> {
                for (Peer peer : peers) {
                    if(peer.isHandshaking()) continue;
                    trackedSwarmDao.upsert(new TrackedSwarmEntity(
                            null,
                            peer.getPeerAddress().getAddress().toNormalizedString(),
                            peer.getPeerAddress().getPort(),
                            torrent.getHash(),
                            downloader.getId(),
                            torrent.getProgress(),
                            peer.getPeerId(),
                            peer.getClientName(),
                            peer.getProgress(),
                            -1L,
                            peer.getUploaded(),
                            peer.getUploadSpeed(),
                            -1L,
                            peer.getDownloaded(),
                            peer.getDownloadSpeed(),
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
