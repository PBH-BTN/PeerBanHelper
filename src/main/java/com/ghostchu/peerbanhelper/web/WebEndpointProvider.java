package com.ghostchu.peerbanhelper.web;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.web.api.PBHBanList;
import com.ghostchu.peerbanhelper.web.api.PBHClientStatus;
import com.ghostchu.peerbanhelper.web.api.PBHMetrics;
import com.ghostchu.peerbanhelper.web.api.TransmissionBlockList;
import fi.iki.elonen.NanoHTTPD;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
@Slf4j
public class WebEndpointProvider extends NanoHTTPD {

    private final PeerBanHelperServer peerBanHelperServer;
    private final List<PBHAPI> apiEndpoints = new ArrayList<>();

    public WebEndpointProvider(int port, PeerBanHelperServer peerBanHelperServer) throws IOException {
        super(port);
        this.peerBanHelperServer = peerBanHelperServer;
        start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        registerEndpoints();
    }

    private void registerEndpoints() {
        apiEndpoints.add(new TransmissionBlockList(peerBanHelperServer));
        apiEndpoints.add(new PBHBanList(peerBanHelperServer));
        apiEndpoints.add(new PBHMetrics(peerBanHelperServer));
        apiEndpoints.add(new PBHClientStatus(peerBanHelperServer));
        apiEndpoints.forEach(apiEndpoint-> log.info(Lang.WEB_ENDPOINT_REGISTERED, apiEndpoint.getClass().getName()));
    }

    @Override
    public Response serve(IHTTPSession session) {
        if (session.getMethod() != Method.GET) {
            return newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, "text/plain", "error method");
        }
        for (PBHAPI apiEndpoint : apiEndpoints) {
            try {
                if (apiEndpoint.shouldHandle(session.getUri())) {
                    return apiEndpoint.handle(session);
                }
            } catch (Exception e) {
                log.error("Failed to handle API request {}", apiEndpoint.getClass().getName(), e);
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Failed to handle api request: " + e.getClass().getName() + ": " + e.getMessage());
            }
        }
        // 尝试处理静态资源
        String uri = session.getUri();
        if(uri.isBlank() || uri.equals("/")){
            uri = "/index.html";
        }
        InputStream is = getClass().getResourceAsStream("/static" +uri);
        if(is == null){
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Error: Resources not found");
        }
        return newChunkedResponse(Response.Status.OK, NanoHTTPD.getMimeTypeForFile(uri), is);
    }


}
