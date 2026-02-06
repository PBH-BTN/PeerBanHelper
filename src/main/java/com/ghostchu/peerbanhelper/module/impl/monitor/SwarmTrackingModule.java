package com.ghostchu.peerbanhelper.module.impl.monitor;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.databasent.service.TrackedSwarmService;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.MonitorFeatureModule;
import com.ghostchu.peerbanhelper.util.query.Orderable;
import com.ghostchu.peerbanhelper.util.query.PBHPage;
import com.ghostchu.peerbanhelper.util.query.Pageable;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import io.javalin.http.Context;
import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component

public final class SwarmTrackingModule extends AbstractFeatureModule implements MonitorFeatureModule {
    @Autowired
    private TrackedSwarmService trackedSwarmDao;
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
        return "peer-analyse-service.swarm-tracking";
    }

    /**
     * 功能模块启用回调
     */
    @Override
    public void onEnable() {
        Main.getEventBus().register(this);
        javalinWebContainer.javalin()
                .get("/api/modules/swarm-tracking", this::handleWebAPI, Role.USER_READ);
        javalinWebContainer.javalin()
                .get("/api/modules/swarm-tracking/details", this::handleDetails, Role.USER_READ);
        trackedSwarmDao.resetTable();
        registerScheduledTask(trackedSwarmDao::flushAll, 0, getConfig().getLong("data-flush-interval"), TimeUnit.MILLISECONDS);
    }

    private void handleDetails(@NotNull Context context) {
        Pageable pageable = new Pageable(context);
        var page = trackedSwarmDao.page(pageable.toPage(), new Orderable(Map.of(), context).apply(new QueryWrapper<>()));
        context.json(new StdResp(true, null, PBHPage.from(page)));
    }

    private void handleWebAPI(@NotNull Context context) {
        Map<String, Object> response = new HashMap<>();
        response.put("trackedSwarmSize", trackedSwarmDao.count());
        context.json(response);
    }

    /**
     * 功能模块禁用回调
     */
    @Override
    public void onDisable() {
        Main.getEventBus().unregister(this);
        trackedSwarmDao.flushAll();
    }

    @Override
    public void onTorrentPeersRetrieved(@NotNull Downloader downloader, @NotNull Torrent torrent, @NotNull List<Peer> peers) {
        try {
            for (Peer peer : peers) {
                if (peer.isHandshaking()) continue;
                trackedSwarmDao.syncPeers(downloader, torrent, peer);
            }
        } catch (ExecutionException e) {
            Sentry.captureException(e);
            log.error("Unable update tracked peers in SQLite temporary table", e);
        }
    }


}
