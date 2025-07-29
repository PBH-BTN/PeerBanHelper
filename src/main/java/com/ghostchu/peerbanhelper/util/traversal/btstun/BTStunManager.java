package com.ghostchu.peerbanhelper.util.traversal.btstun;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.downloader.DownloaderManager;
import com.ghostchu.peerbanhelper.util.PBHPortMapper;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

// todo: 需要添加一个定时器，只有下载器状态正常才启动 STUN，不正常了特别是连不上就关闭 STUN
@Component
@Slf4j
public class BTStunManager implements AutoCloseable, Reloadable {
    private final Map<Downloader, BTStunInstance> perDownloaderStun = Collections.synchronizedMap(new HashMap<>());
    private final DownloaderManager downloaderManager;
    private final PBHPortMapper pBHPortMapper;
    private boolean enabled = false;

    public BTStunManager(DownloaderManager downloaderManager, PBHPortMapper pBHPortMapper) {
        this.downloaderManager = downloaderManager;
        this.pBHPortMapper = pBHPortMapper;
        load();
        Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().unstarted(() -> {
            try {
                close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }));
        Main.getEventBus().register(this);
    }

    private void load() {
        var autoStun = Main.getMainConfig().getConfigurationSection("auto-stun");
        if (!autoStun.getBoolean("enabled", false)) {
            enabled = false;
            return;
        }
        for (String downloaderId : autoStun.getStringList("downloaders")) {
            var downloader = downloaderManager.getDownloaderById(downloaderId);
            if (downloader == null) {
                continue; // 静默失败
            }
            perDownloaderStun.put(downloader, new BTStunInstance(pBHPortMapper, downloader));
        }
        enabled = true;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Nullable
    public BTStunInstance getStunInstance(@NotNull Downloader downloader) {
        return perDownloaderStun.get(downloader);
    }

    public Map<Downloader, BTStunInstance> getDownloadStunInstances() {
        return perDownloaderStun;
    }

    @Override
    public ReloadResult reloadModule() throws Exception {
        close();
        load();
        return Reloadable.super.reloadModule();
    }


    @Override
    public void close() throws Exception {
        perDownloaderStun.values().forEach(instance -> {
            try {
                instance.close();
            } catch (Exception ignored) {
            }
        });
        enabled = false;
    }
}
