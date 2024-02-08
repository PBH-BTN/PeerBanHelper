package com.ghostchu.peerbanhelper;

import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.module.BanResult;
import com.ghostchu.peerbanhelper.module.FeatureModule;
import com.ghostchu.peerbanhelper.module.impl.ClientNameBlacklist;
import com.ghostchu.peerbanhelper.module.impl.IPBlackList;
import com.ghostchu.peerbanhelper.module.impl.PeerIdBlacklist;
import com.ghostchu.peerbanhelper.module.impl.ProgressCheatBlocker;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class PeerBanHelperServer {
    private final Map<PeerAddress, BanMetadata> BAN_LIST = new ConcurrentHashMap<>();
    private final Timer PEER_CHECK_TIMER = new Timer("Peer check");
    private final YamlConfiguration profile;
    private final List<Downloader> downloaders;
    private final long banDuration;
    private final int httpdPort;
    private List<FeatureModule> registeredModules = new ArrayList<>();
    private BlacklistProvider blacklistProviderServer;

    public PeerBanHelperServer(List<Downloader> downloaders, YamlConfiguration profile, int httpdPort) {
        this.downloaders = downloaders;
        this.profile = profile;
        this.banDuration = profile.getLong("ban-duration");
        this.httpdPort = httpdPort;
        registerModules();
        registerTimer();
       // registerBlacklistHttpServer();
    }

    private void registerBlacklistHttpServer() {
        try {
            this.blacklistProviderServer = new BlacklistProvider(httpdPort, this);
        } catch (IOException e) {
            log.warn("无法初始化 API 提供端点",e);
        }
    }

    private void registerModules() {
        log.info("正在启动功能模块……");
        this.registeredModules.clear();
        List<FeatureModule> modules = new ArrayList<>();
        modules.add(new IPBlackList(profile));
        modules.add(new PeerIdBlacklist(profile));
        modules.add(new ClientNameBlacklist(profile));
        modules.add(new ProgressCheatBlocker(profile));
        this.registeredModules.addAll(modules.stream().filter(FeatureModule::isModuleEnabled).toList());
        this.registeredModules.forEach(m -> log.info("[注册] {}", m.getName()));
    }

    private void registerTimer() {
        PEER_CHECK_TIMER.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                banWave();
            }
        }, 0, profile.getLong("check-interval"));
    }

    public void banWave() {
        boolean needUpdate = false;
        for (Downloader downloader : this.downloaders) {
            try {
                if (!downloader.login()) {
                    log.warn("登录到 {} ({}) 失败，跳过……", downloader.getName(), downloader.getEndpoint());
                    continue;
                }
                if (banDownloader(downloader)) {
                    needUpdate = true;
                }
            } catch (Throwable th) {
                log.warn("在处理 {} ({}) 的 WebAPI 操作时出现了一个非预期的错误", downloader.getName(), downloader.getEndpoint(), th);
            }
        }

        List<PeerAddress> removeBan = new ArrayList<>();
        for (Map.Entry<PeerAddress, BanMetadata> pair : BAN_LIST.entrySet()) {
            if (System.currentTimeMillis() >= pair.getValue().getUnbanAt()) {
                removeBan.add(pair.getKey());
            }
        }

        removeBan.forEach(BAN_LIST::remove);
        if (!removeBan.isEmpty()) {
            log.info("[解封] 解除了 " + removeBan.size() + " 个过期的对等体封禁");
            needUpdate = true;
        }

        if (needUpdate) {
            for (Downloader downloader : this.downloaders) {
                try {
                    if (!downloader.login()) {
                        log.warn("登录到 {} ({}) 失败，跳过……", downloader.getName(), downloader.getEndpoint());
                        continue;
                    }
                    downloader.setBanList(BAN_LIST.keySet());
                } catch (Throwable th) {
                    log.warn("在更新 {} ({}) 的 BanList 时出现了一个非预期的错误", downloader.getName(), downloader.getEndpoint(), th);
                }
            }
        }
    }


    private boolean banDownloader(Downloader downloader) {
        boolean needUpdate = false;
        Map<Torrent, List<Peer>> map = new HashMap<>();
        int peers = 0;
        for (Torrent torrent : downloader.getTorrents()) {
            map.put(torrent, downloader.getPeers(torrent));
        }
        for (Map.Entry<Torrent, List<Peer>> pair : map.entrySet()) {
            peers += pair.getValue().size();
            for (Peer peer : pair.getValue()) {
                BanResult banResult = checkBan(pair.getKey(), peer);
                if (banResult != null) {
                    needUpdate = true;
                    BAN_LIST.put(peer.getAddress(), new BanMetadata(UUID.randomUUID(), System.currentTimeMillis(), System.currentTimeMillis() + banDuration, banResult.reason()));
                    log.warn("[封禁] {}, PeerId={}, ClientName={}, Progress={}, Uploaded={}, Downloaded={}, Reason={}", peer.getAddress(), peer.getPeerId(), peer.getClientName(), peer.getProgress(), peer.getUploaded(), peer.getDownloaded(), banResult.reason());
                }
            }
        }
        log.info("[完成] 已检查 {} 的 {} 个活跃 Torrent 和 {} 个对等体", downloader.getName(), map.keySet().size(), peers);
        return needUpdate;
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
