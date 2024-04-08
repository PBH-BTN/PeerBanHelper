package com.ghostchu.peerbanhelper.web.api;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.web.PBHAPI;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import fi.iki.elonen.NanoHTTPD;
import inet.ipaddr.IPAddressString;

import java.util.Map;

public class TransmissionBlockList implements PBHAPI {
    private final PeerBanHelperServer server;

    public TransmissionBlockList(PeerBanHelperServer server) {
        this.server = server;
    }

    @Override
    public boolean shouldHandle(String uri) {
        return uri.equals("/blocklist/transmission");
    }

    @Override
    public NanoHTTPD.Response handle(NanoHTTPD.IHTTPSession session) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<PeerAddress, BanMetadata> pair : server.getBannedPeers().entrySet()) {
            builder.append(new IPAddressString(pair.getKey().getIp()).getAddress().assignPrefixForSingleBlock().toString()).append("\n");
        }
        return NanoHTTPD.newFixedLengthResponse(builder.toString());
    }
}
