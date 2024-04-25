package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
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
        long limit = Long.parseLong(session.getParameters().getOrDefault("limit", List.of("-1")).get(0));
        long lastBanTime = Long.parseLong(session.getParameters().getOrDefault("lastBanTime", List.of("-1")).get(0));
        var banResponseList = getServer().getBannedPeers()
                .entrySet()
                .stream()
                .map(entry -> new BanResponse(entry.getKey().getAddress().toString(), entry.getValue()))
                .sorted((o1, o2) -> Long.compare(o2.getBanMetadata().getBanAt(), o1.getBanMetadata().getBanAt()));
        if (lastBanTime > 0) {
            banResponseList = banResponseList.filter(b -> b.getBanMetadata().getBanAt() < lastBanTime);
        }
        if (limit > 0) {
            banResponseList = banResponseList.limit(limit);
        }

        String JSON = JsonUtil.prettyPrinting().toJson(banResponseList.toList());
        System.out.println(JSON);
        return HTTPUtil.cors(NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", JSON));
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


    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    static class BanResponse {
        private String address;
        private BanMetadata banMetadata;
    }
}
