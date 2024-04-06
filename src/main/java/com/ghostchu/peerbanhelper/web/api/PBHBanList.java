package com.ghostchu.peerbanhelper.web.api;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.util.JsonUtil;
import com.ghostchu.peerbanhelper.web.PBHAPI;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import fi.iki.elonen.NanoHTTPD;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public class PBHBanList implements PBHAPI {
    private final PeerBanHelperServer server;

    public PBHBanList(PeerBanHelperServer server) {
        this.server = server;
    }

    @Override
    public boolean shouldHandle(String uri) {
        return uri.equals("/api/banlist");
    }

    @Override
    public NanoHTTPD.Response handle(NanoHTTPD.IHTTPSession session) {
        String JSON = JsonUtil.prettyPrinting().toJson(server.getBannedPeers().entrySet().stream().map(entry->new BanResponse(entry.getKey(), entry.getValue())).toList());
        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", JSON);
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    static class BanResponse{
        private PeerAddress address;
        private BanMetadata banMetadata;
    }
}
