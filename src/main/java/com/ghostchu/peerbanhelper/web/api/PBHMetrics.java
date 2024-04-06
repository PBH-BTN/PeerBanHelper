package com.ghostchu.peerbanhelper.web.api;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.metric.Metrics;
import com.ghostchu.peerbanhelper.util.JsonUtil;
import com.ghostchu.peerbanhelper.web.PBHAPI;
import fi.iki.elonen.NanoHTTPD;

import java.util.HashMap;
import java.util.Map;

public class PBHMetrics implements PBHAPI {
    private final PeerBanHelperServer server;
    private final Metrics metrics;

    public PBHMetrics(PeerBanHelperServer server){
        this.server = server;
        this.metrics = server.getMetrics();
    }
    @Override
    public boolean shouldHandle(String uri) {
        return uri.equals("/api/statistic");
    }

    @Override
    public NanoHTTPD.Response handle(NanoHTTPD.IHTTPSession session) {
        Map<String, Object> map = new HashMap<>();
        map.put("checkCounter", metrics.getCheckCounter());
        map.put("peerBanCounter", metrics.getPeerBanCounter());
        map.put("peerUnbanCounter", metrics.getPeerUnbanCounter());
        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", JsonUtil.prettyPrinting().toJson(map));
    }
}
