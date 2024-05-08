package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.database.DatabaseHelper;
import com.ghostchu.peerbanhelper.metric.impl.persist.PersistMetrics;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.JsonUtil;
import com.ghostchu.peerbanhelper.web.PBHAPI;
import fi.iki.elonen.NanoHTTPD;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class PBHBanLogs extends AbstractFeatureModule implements PBHAPI {
    private final DatabaseHelper db;

    public PBHBanLogs(PeerBanHelperServer server, YamlConfiguration profile, @NotNull DatabaseHelper db) {
      super(server,profile);
      this.db = db;
    }

    @Override
    public boolean shouldHandle(String uri) {
        return uri.equals("/api/banlogs");
    }

    @Override
    public NanoHTTPD.Response handle(NanoHTTPD.IHTTPSession session) {
        if (db == null) {
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NO_CONTENT, "text/plain", "Database persist not enabled on this PBH server");
        }
        if (getServer().getMetrics() instanceof PersistMetrics persistMetrics) {
            persistMetrics.flush();
        }

        int pageIndex = Integer.parseInt(session.getParameters().getOrDefault("pageIndex", List.of("0")).get(0));
        int pageSize = Integer.parseInt(session.getParameters().getOrDefault("pageSize", List.of("100")).get(0));
        try {
            Map<String, Object> map = new HashMap<>();
            map.put("pageIndex", pageIndex);
            map.put("pageSize", pageSize);
            map.put("results", db.queryBanLogs(null, null, pageIndex, pageSize));
            map.put("total", db.queryBanLogsCount());
            return HTTPUtil.cors(NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", JsonUtil.prettyPrinting().toJson(map)));
        } catch (SQLException e) {
            log.error(Lang.WEB_BANLOGS_INTERNAL_ERROR, e);
            return HTTPUtil.cors(NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, "text/plain", "Internal server error, please check the console"));
        }
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public @NotNull String getName() {
        return "WebAPI - PBH BanLogs";
    }

    @Override
    public @NotNull String getConfigName() {
        return "webapi-banlogs";
    }

    @Override
    public void onEnable() {
        getServer().getWebManagerServer().register(this);
    }

    @Override
    public void onDisable() {
        getServer().getWebManagerServer().unregister(this);
    }

}
