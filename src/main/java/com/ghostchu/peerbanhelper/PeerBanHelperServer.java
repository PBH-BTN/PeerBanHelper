package com.ghostchu.peerbanhelper;

import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.module.BanResult;
import com.ghostchu.peerbanhelper.module.FeatureModule;
import com.ghostchu.peerbanhelper.module.impl.*;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class PeerBanHelperServer {
    private final Map<PeerAddress, BanMetadata> BAN_LIST = new ConcurrentHashMap<>();
    private final Timer PEER_CHECK_TIMER = new Timer("Peer check");
    private final YamlConfiguration profile;
    private final List<Downloader> downloaders;
    private final long banDuration;
    private final int httpdPort;
    private final boolean hideFinishLogs;
    private List<FeatureModule> registeredModules = new ArrayList<>();
    private BlacklistProvider blacklistProviderServer;

    public PeerBanHelperServer(List<Downloader> downloaders, YamlConfiguration profile, int httpdPort, boolean hideFinishLogs) {
        this.downloaders = downloaders;
        this.profile = profile;
        this.banDuration = profile.getLong("ban-duration");
        this.httpdPort = httpdPort;
        this.hideFinishLogs = hideFinishLogs;
        registerModules();
        registerTimer();
        registerBlacklistHttpServer();
    }

    private void registerBlacklistHttpServer() {
        try {
            this.blacklistProviderServer = new BlacklistProvider(httpdPort, this);
        } catch (IOException e) {
            log.warn(Lang.ERR_INITIALIZE_BAN_PROVIDER_ENDPOINT_FAILURE, e);
        }
    }

    private void registerModules() {
        log.info(Lang.WAIT_FOR_MODULES_STARTUP);
        this.registeredModules.clear();
        List<FeatureModule> modules = new ArrayList<>();
        modules.add(new IPBlackList(profile));
        modules.add(new PeerIdBlacklist(profile));
        modules.add(new ClientNameBlacklist(profile));
        modules.add(new ProgressCheatBlocker(profile));
        modules.add(new ActiveProbing(profile));
        this.registeredModules.addAll(modules.stream().filter(FeatureModule::isModuleEnabled).toList());
        this.registeredModules.forEach(m -> log.info(Lang.MODULE_REGISTER, m.getName()));
    }

    private void registerTimer() {
        PEER_CHECK_TIMER.schedule(new TimerTask() {
            @Override
            public void run() {
                banWave();
            }
        }, 0, profile.getLong("check-interval", 5000));
    }

    public void banWave() {
        boolean needUpdate = false;
        Set<Torrent> needRelaunched = new HashSet<>();
        for (Downloader downloader : this.downloaders) {
            try {
                if (!downloader.login()) {
                    log.warn(Lang.ERR_CLIENT_LOGIN_FAILURE_SKIP, downloader.getName(), downloader.getEndpoint());
                    continue;
                }
                Pair<Boolean, Collection<Torrent>> banDownloader = banDownloader(downloader);
                if (banDownloader.getKey()) {
                    needUpdate = true;
                }
                needRelaunched.addAll(banDownloader.getValue());
            } catch (Throwable th) {
                log.warn(Lang.ERR_UNEXPECTED_API_ERROR, downloader.getName(), downloader.getEndpoint(), th);
            }
        }

        List<PeerAddress> removeBan = new ArrayList<>();
        for (Map.Entry<PeerAddress, BanMetadata> pair : BAN_LIST.entrySet()) {
            if (System.currentTimeMillis() >= pair.getValue().getUnbanAt()) {
                removeBan.add(pair.getKey());
            }
        }

        removeBan.forEach(this::unbanPeer);
        if (!removeBan.isEmpty()) {
            log.info(Lang.PEER_UNBAN_WAVE, removeBan.size());
            needUpdate = true;
        }

        if (needUpdate) {
            this.downloaders.parallelStream().forEach(downloader -> {
                try {
                    if (!downloader.login()) {
                        log.warn(Lang.ERR_CLIENT_LOGIN_FAILURE_SKIP, downloader.getName(), downloader.getEndpoint());
                        return;
                    }
                    downloader.setBanList(BAN_LIST.keySet());
                    downloader.relaunchTorrentIfNeeded(needRelaunched);
                } catch (Throwable th) {
                    log.warn(Lang.ERR_UPDATE_BAN_LIST, downloader.getName(), downloader.getEndpoint(), th);
                }
            });
        }
    }


    private Pair<Boolean, Collection<Torrent>> banDownloader(Downloader downloader) {
        AtomicBoolean needUpdate = new AtomicBoolean(false);
        Map<Torrent, List<Peer>> map = new ConcurrentHashMap<>();
        Set<Torrent> needRelaunched = new CopyOnWriteArraySet<>();
        AtomicInteger peers = new AtomicInteger(0);

        downloader.getTorrents().parallelStream().forEach(torrent -> map.put(torrent, downloader.getPeers(torrent)));

        map.entrySet().parallelStream().forEach(pair -> {
            peers.addAndGet(pair.getValue().size());
            pair.getValue().parallelStream().forEach(peer -> {
                BanResult banResult = checkBan(pair.getKey(), peer);
                if (banResult != null) {
                    needUpdate.set(true);
                    needRelaunched.add(pair.getKey());
                    banPeer(peer.getAddress(), new BanMetadata(UUID.randomUUID(), System.currentTimeMillis(), System.currentTimeMillis() + banDuration, banResult.reason()));
                    log.warn(Lang.BAN_PEER, peer.getAddress(), peer.getPeerId(), peer.getClientName(), peer.getProgress(), peer.getUploaded(), peer.getDownloaded(), banResult.reason());
                }
            });
        });
        if (!hideFinishLogs) {
            log.info(Lang.CHECK_COMPLETED, downloader.getName(), map.keySet().size(), peers);
        }
        return Pair.of(needUpdate.get(), needRelaunched);
    }

    private BanResult checkBan(Torrent torrent, Peer peer) {
        BanResult banResult = null;
        for (FeatureModule registeredModule : registeredModules) {
            BanResult result = registeredModule.shouldBanPeer(torrent, peer);
            if (result.ban()) {
                banResult = result;
                break;
            }
        }
        return banResult;
    }

    @NotNull
    public Map<PeerAddress, BanMetadata> getBannedPeers() {
        return ImmutableMap.copyOf(BAN_LIST);
    }

    public void banPeer(@NotNull PeerAddress peer, @NotNull BanMetadata banMetadata) {
        BAN_LIST.put(peer, banMetadata);
    }


    public void unbanPeer(@NotNull PeerAddress address) {
        BAN_LIST.remove(address);
    }
}
