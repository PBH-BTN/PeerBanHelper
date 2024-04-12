package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.BanResult;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.JsonUtil;
import com.ghostchu.peerbanhelper.web.PBHAPI;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import fi.iki.elonen.NanoHTTPD;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.ExecutorService;

public class PBHBanList extends AbstractFeatureModule implements PBHAPI {

    public PBHBanList(PeerBanHelperServer server, YamlConfiguration profile) {
       super(server, profile);
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public boolean shouldHandle(String uri) {
        return uri.equals("/api/banlist");
    }

    @Override
    public NanoHTTPD.Response handle(NanoHTTPD.IHTTPSession session) {
        List<BanResponse> banResponseList = getServer().getBannedPeers().entrySet().stream().map(entry -> new BanResponse(entry.getKey().getAddress().toString(), entry.getValue())).toList();
        String JSON = JsonUtil.prettyPrinting().toJson(banResponseList);
        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", JSON);
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
        return "WebAPI - PBH BanList";
    }

    @Override
    public @NotNull String getConfigName() {
        return "webapi-banlist";
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
