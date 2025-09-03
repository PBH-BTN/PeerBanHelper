package com.ghostchu.peerbanhelper.util.traversal.btstun;

import com.ghostchu.peerbanhelper.BanList;
import com.ghostchu.peerbanhelper.DownloaderServer;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.downloader.DownloaderFeatureFlag;
import com.ghostchu.peerbanhelper.downloader.DownloaderLastStatus;
import com.ghostchu.peerbanhelper.downloader.DownloaderManager;
import com.ghostchu.peerbanhelper.util.lab.Laboratory;
import com.ghostchu.peerbanhelper.util.portmapper.PBHPortMapper;
import com.ghostchu.peerbanhelper.util.traversal.NatAddressProviderRegistry;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class BTStunManager implements AutoCloseable, Reloadable {
    private final Map<Downloader, BTStunInstance> perDownloaderStun = Collections.synchronizedMap(new HashMap<>());
    private final DownloaderManager downloaderManager;
    private final PBHPortMapper pBHPortMapper;
    private final DownloaderServer downloaderServer;
    private final NatAddressProviderRegistry natAddressProviderRegistry;
    private final BanList banList;
    private final Laboratory laboratory;
    private boolean enabled = false;
    private final ScheduledExecutorService sched = Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory());

    public BTStunManager(BanList banList, DownloaderManager downloaderManager, PBHPortMapper pBHPortMapper, DownloaderServer downloaderServer, NatAddressProviderRegistry natAddressProviderRegistry, Laboratory laboratory) {
        this.banList = banList;
        this.downloaderManager = downloaderManager;
        this.pBHPortMapper = pBHPortMapper;
        this.downloaderServer = downloaderServer;
        this.natAddressProviderRegistry = natAddressProviderRegistry;
        load();
        Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().unstarted(() -> {
            try {
                close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }));
        Main.getEventBus().register(this);
        this.laboratory = laboratory;
    }

    private void load() {
        var autoStun = Main.getMainConfig().getConfigurationSection("auto-stun");
        if (!autoStun.getBoolean("enabled", false)) {
            enabled = false;
            return;
        }
        sched.scheduleWithFixedDelay(this::scanAndLoad, 0, 5, TimeUnit.SECONDS);
        enabled = true;
    }

    private void scanAndLoad() {
        var autoStun = Main.getMainConfig().getConfigurationSection("auto-stun");
        for (String downloaderId : autoStun.getStringList("downloaders")) {
            var downloader = downloaderManager.getDownloaderById(downloaderId);
            if (downloader == null) {
                continue; // 静默失败
            }
            if (downloader.getLastStatus() != DownloaderLastStatus.HEALTHY) {
                if (downloader.getFailedLoginAttempts() > 10) {
                    //log.warn(tlUI(Lang.BTSTUN_SHUTDOWN_DOWNLOADER_OFFLINE, downloader.getName()));
                    unregister(downloader);
                }
            } else {
                //log.info(tlUI(Lang.BTSTUN_SHUTDOWN_DOWNLOADER_ONLINE, downloader.getName()));
                register(downloader);
            }
        }
    }

    public boolean register(Downloader downloader) {
        if (perDownloaderStun.containsKey(downloader)) {
            //log.debug("Duplicate registration for downloader: {}", downloader.getId());
            return false;
        }
        if (!downloader.login().success()) {
            log.debug("Login failed for downloader: {}", downloader.getId());
            return false;
        }
        if (!downloader.getFeatureFlags().contains(DownloaderFeatureFlag.LIVE_UPDATE_BT_PROTOCOL_PORT)) {
            log.debug("Downloader does not support live update of BT protocol port: {}", downloader.getId());
            return false;
        }
        var instance = new BTStunInstance(banList, pBHPortMapper, downloader, this, laboratory);
        perDownloaderStun.put(downloader, instance);
        natAddressProviderRegistry.add(instance);
        return true;
    }

    public void unregister(Downloader downloader) {
        var instance = perDownloaderStun.remove(downloader);
        natAddressProviderRegistry.remove(instance);
        if (instance != null) {
            try {
                instance.close();
            } catch (Exception e) {
                log.error("Failed to close BTStunInstance for downloader: {}", downloader.getId(), e);
            }
        }
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
    public void close() {
        perDownloaderStun.values().forEach(instance -> {
            try {
                instance.close();
                natAddressProviderRegistry.remove(instance);
            } catch (Exception ignored) {
            }
        });
        perDownloaderStun.clear();
        enabled = false;
    }


}
