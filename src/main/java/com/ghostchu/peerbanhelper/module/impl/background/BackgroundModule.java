package com.ghostchu.peerbanhelper.module.impl.background;

import com.ghostchu.peerbanhelper.database.dao.impl.TorrentDao;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.util.context.IgnoreScan;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@IgnoreScan
public final class BackgroundModule extends AbstractFeatureModule implements Reloadable {
    private final TorrentDao torrentDao;
    private ScheduledExecutorService pool;

    public BackgroundModule(TorrentDao torrentDao) {
        super();
        this.torrentDao = torrentDao;
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public @NotNull String getName() {
        return "Background SQLite Database Optimizer";
    }

    @Override
    public @NotNull String getConfigName() {
        return "background-sqlite-database-optimizer";
    }

    @Override
    public void onEnable() {
        this.pool = Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory());
        this.pool.scheduleWithFixedDelay(this::runOptimizeTask, 1, 1, TimeUnit.HOURS);
    }

    private void runOptimizeTask() {
        try {
            torrentDao.executeRaw("PRAGMA optimize;");
        } catch (SQLException e) {
            log.warn("Failed to perform period SQLite database optimization", e);
        }
    }

    @Override
    public ReloadResult reloadModule() throws Exception {
        return Reloadable.super.reloadModule();
    }

    @Override
    public void onDisable() {
        if (pool != null) {
            pool.shutdownNow();
        }
    }

}
