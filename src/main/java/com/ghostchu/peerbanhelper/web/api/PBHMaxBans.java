package com.ghostchu.peerbanhelper.web.api;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.database.DatabaseHelper;
import com.ghostchu.peerbanhelper.util.JsonUtil;
import com.ghostchu.peerbanhelper.web.PBHAPI;
import fi.iki.elonen.NanoHTTPD;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class PBHMaxBans implements PBHAPI {
    private final PeerBanHelperServer server;
    private final DatabaseHelper db;

    public PBHMaxBans(PeerBanHelperServer server, DatabaseHelper db) {
        this.server = server;
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
           return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", JsonUtil.getGson().toJson(list));
        } catch (SQLException e) {
           log.warn("Error on handling Web API request", e);
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, "text/plain", "Internal server error, please check the console");
        }
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    static class HistoryEntry {
        private String address;
        private long count;
    }
}
