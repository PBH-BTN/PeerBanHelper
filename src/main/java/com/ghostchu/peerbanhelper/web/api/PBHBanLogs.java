package com.ghostchu.peerbanhelper.web.api;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.database.DatabaseHelper;
import com.ghostchu.peerbanhelper.metric.impl.persist.PersistMetrics;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.JsonUtil;
import com.ghostchu.peerbanhelper.web.PBHAPI;
import fi.iki.elonen.NanoHTTPD;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.List;

@Slf4j
public class PBHBanLogs implements PBHAPI {
    private final PeerBanHelperServer server;
    private final DatabaseHelper db;

    public PBHBanLogs(PeerBanHelperServer server, @Nullable DatabaseHelper db) {
        this.server = server;
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
        if (server.getMetrics() instanceof PersistMetrics persistMetrics) {
            persistMetrics.flush();
        }
        int pageIndex = Integer.parseInt(session.getParameters().getOrDefault("pageIndex", List.of("0")).get(0));
        int pageSize = Integer.parseInt(session.getParameters().getOrDefault("pageSize", List.of("100")).get(0));
        try {
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", JsonUtil.prettyPrinting().toJson(db.queryBanLogs(null, null, pageIndex, pageSize)));
        } catch (SQLException e) {
            log.error(Lang.WEB_BANLOGS_INTERNAL_ERROR, e);
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, "text/plain", "Internal server error, please check the console");
        }
    }
}
