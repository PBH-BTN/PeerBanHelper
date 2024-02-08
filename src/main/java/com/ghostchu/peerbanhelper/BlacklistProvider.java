package com.ghostchu.peerbanhelper;

import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import fi.iki.elonen.NanoHTTPD;

import java.io.IOException;
import java.util.Map;

public class BlacklistProvider extends NanoHTTPD {

    private final PeerBanHelperServer peerBanHelperServer;

    public BlacklistProvider(int port, PeerBanHelperServer peerBanHelperServer) throws IOException {
        super(port);
        this.peerBanHelperServer = peerBanHelperServer;
        start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
    }

    @Override
    public Response serve(IHTTPSession session) {
        if(session.getMethod() != Method.GET){
            return newFixedLengthResponse("error method");
        }
        if(session.getUri().equals("/blocklist/transmission")){
            return newFixedLengthResponse(bakeTransmissionBlockList());
        }
        return newFixedLengthResponse("wrong endpoint");
    }

    private String bakeTransmissionBlockList() {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<PeerAddress, BanMetadata> pair : peerBanHelperServer.getBannedPeers().entrySet()) {
            builder.append(pair.getValue().getRandomId()).append(":").append(pair.getKey().getIp()).append("-").append(pair.getKey().getIp()).append("\n");
        }
        return builder.toString();
    }
}
