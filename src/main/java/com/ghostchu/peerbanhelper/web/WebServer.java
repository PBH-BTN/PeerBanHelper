package com.ghostchu.peerbanhelper.web;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.config.ConfigManager;
import com.ghostchu.peerbanhelper.text.Lang;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
public class WebServer {

    private PeerBanHelperServer server;

    @Getter
    private WebEndpointProvider webEndpointProviderServer;

    public WebServer(PeerBanHelperServer server) {
        this.server = server;
    }

    public void start() {
        int httpdPort = ConfigManager.Sections.server().getPort();

        try {
            this.webEndpointProviderServer = new WebEndpointProvider(httpdPort, server);
        } catch (IOException e) {
            log.warn(Lang.ERR_INITIALIZE_BAN_PROVIDER_ENDPOINT_FAILURE, e);
        }
    }

    public void stop() {
        this.webEndpointProviderServer.stop();
    }

    public void restart() {
        stop();
        start();
    }

}
