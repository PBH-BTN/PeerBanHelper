package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.database.DatabaseHelper;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.BanResult;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.JsonUtil;
import com.ghostchu.peerbanhelper.web.PBHAPI;
import fi.iki.elonen.NanoHTTPD;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

@Slf4j
public class PBHMaxBans extends AbstractFeatureModule implements PBHAPI {
    private final DatabaseHelper db;

    public PBHMaxBans(PeerBanHelperServer server, YamlConfiguration profile, @NotNull DatabaseHelper db) {
      super(server,profile);
      this.db = db;
    }

    @Override
    public boolean shouldHandle(String uri) {
        return uri.equals("/api/maxbans");
    }

    @Override
    public NanoHTTPD.Response handle(NanoHTTPD.IHTTPSession session) {
        int number = Integer.parseInt(session.getParameters().getOrDefault("num", List.of("50")).get(0));

        try {
            Map<String, Long> countMap = db.findMaxBans(number);
            List<HistoryEntry> list = new ArrayList<>(countMap.size());
            countMap.forEach((k,v)-> {
                if(v >= 2) {
                    list.add(new HistoryEntry(k, v));
                }
            });
            return HTTPUtil.cors( NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", JsonUtil.getGson().toJson(list)));
        } catch (SQLException e) {
            log.warn("Error on handling Web API request", e);
            return HTTPUtil.cors(NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, "text/plain", "Internal server error, please check the console"));
        }
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public @NotNull String getName() {
        return "WebAPI - Max Bans";
    }

    @Override
    public @NotNull String getConfigName() {
        return "webapi-maxbans";
    }

    @Override
    public boolean isCheckCacheable() {
        return true;
    }

    @Override
    public boolean needCheckHandshake() {
        return false;
    }

    @Override
    public @NotNull BanResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull ExecutorService ruleExecuteExecutor) {
        return teapot();
    }

    @Override
    public void onEnable() {
        getServer().getWebManagerServer().register(this);
    }

    @Override
    public void onDisable() {
        getServer().getWebManagerServer().unregister(this);
    }
    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    static class HistoryEntry {
        private String address;
        private long count;
    }

}
