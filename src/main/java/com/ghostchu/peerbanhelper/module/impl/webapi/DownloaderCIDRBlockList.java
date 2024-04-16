package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.BanResult;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.web.PBHAPI;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import fi.iki.elonen.NanoHTTPD;
import inet.ipaddr.IPAddressString;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ExecutorService;

public class DownloaderCIDRBlockList extends AbstractFeatureModule implements PBHAPI {
    public DownloaderCIDRBlockList(PeerBanHelperServer server, YamlConfiguration profile) {
       super(server, profile);
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public boolean shouldHandle(String uri) {
        return uri.equals("/blocklist/transmission");
    }

    @Override
    public NanoHTTPD.Response handle(NanoHTTPD.IHTTPSession session) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<PeerAddress, BanMetadata> pair : getServer().getBannedPeers().entrySet()) {
            builder.append(new IPAddressString(pair.getKey().getIp()).getAddress().assignPrefixForSingleBlock().toString()).append("\n");
        }
        return HTTPUtil.cors(NanoHTTPD.newFixedLengthResponse(builder.toString()));
    }

    @Override
    public void onEnable() {
        getServer().getWebManagerServer().register(this);
    }

    @Override
    public void onDisable() {
        getServer().getWebManagerServer().unregister(this);
    }

    @Override
    public @NotNull String getName() {
        return "WebAPI - Downloader CIDR Blocklist";
    }

    @Override
    public @NotNull String getConfigName() {
        return "webapi-downloader-cidr-blocklist";
    }

    @Override
    public boolean needCheckHandshake() {
        return false;
    }

    @Override
    public @NotNull BanResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull ExecutorService ruleExecuteExecutor) {
        return teapot();
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    static class BanResponse {
        private String address;
        private BanMetadata banMetadata;
    }
}
