package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.BanResult;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.torrent.Torrent;
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

import java.util.Arrays;
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
        long limit = Long.parseLong(session.getParameters().getOrDefault("limit", List.of("-1")).get(0));
        long lastBanTime = Long.parseLong(session.getParameters().getOrDefault("lastBanTime", List.of("-1")).get(0));
        List<BanResponse> banResponseList = getServer().getBannedPeers()
                .entrySet()
                .stream()
                .map(entry -> new BanResponse(entry.getKey().getAddress().toString(), entry.getValue()))
                .sorted((o1, o2) -> Long.compare(o2.getBanMetadata().getBanAt(), o1.getBanMetadata().getBanAt()))
                .toList();
        if (lastBanTime > 0) { // 这里已经排序了，所以不需要遍历所有
            var pick = new BanResponse[limit > 0 ? (int) limit : banResponseList.size()];
            int startIndex = -1;
            for (int i = 0; i < banResponseList.size(); i++) {
                if (banResponseList.get(i).getBanMetadata().getBanAt() < lastBanTime) { //找到第一个小于lastBanTime的
                    startIndex = i;
                    break;
                }
            }
            if (startIndex == -1) {// not found
                return HTTPUtil.cors(NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", "[]"));
            } else {
                for (int i = 0; i < pick.length; i++) { // 从第一个小于lastBanTime的开始取，往后去count个就行
                    if (startIndex + i >= banResponseList.size()) { // 数量不够了，直接返回
                        pick = Arrays.copyOfRange(pick, 0, i);
                        break;
                    }
                    pick[i] = banResponseList.get(startIndex + i);
                }
                banResponseList = List.of(pick);
            }
        }
        if (limit > 0) {
            banResponseList = banResponseList.stream().limit(limit).toList();
        }
        String JSON = JsonUtil.prettyPrinting().toJson(banResponseList);
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
