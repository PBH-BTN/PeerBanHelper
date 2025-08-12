package com.ghostchu.peerbanhelper.util.traversal.stun.tunnel;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.portmapper.PBHPortMapper;
import com.ghostchu.peerbanhelper.util.portmapper.Protocol;
import com.ghostchu.peerbanhelper.util.traversal.stun.StunListener;
import com.ghostchu.peerbanhelper.util.traversal.stun.StunSocketTool;
import com.ghostchu.peerbanhelper.util.traversal.stun.TcpStunClient;
import com.sun.net.httpserver.HttpServer;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.StandardSocketOptions;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class StunTcpTunnelImpl implements StunTcpTunnel {
    private final StunListener stunListener;
    private final ScheduledExecutorService keepAliveService = Executors.newScheduledThreadPool(1, runnable -> Thread.ofVirtual().name("StunTcpTunnel-KeepAlive").unstarted(runnable));
    private final AtomicBoolean valid = new AtomicBoolean(false);
    private final PBHPortMapper pbhPortMapper;
    private Socket keepAliveSocket;
    private long startedAt;
    private long lastSuccessHeartbeatAt;

    public StunTcpTunnelImpl(PBHPortMapper pbhPortMapper, StunListener stunListener) {
        this.pbhPortMapper = pbhPortMapper;
        this.stunListener = stunListener;
    }

    @Override
    public void createMapping(int localPort) throws IOException {
        startedAt = System.currentTimeMillis();
        if (localPort == 0) {
            @Cleanup
            var tmpSocket = new ServerSocket(0);
            localPort = tmpSocket.getLocalPort();
            tmpSocket.close();
        }
        pbhPortMapper.mapPort(localPort, Protocol.TCP, "PeerBanHelper STUN Hole Puncher (TCP/" + localPort + ")").join();
        TcpStunClient tcpStunClient = new TcpStunClient(Main.getMainConfig().getStringList("stun.tcp-servers"), "0.0.0.0", localPort);
        var mappingResult = tcpStunClient.getMapping();
        var interResult = mappingResult.interAddress();
        var outerResult = mappingResult.outerAddress();
        log.debug("STUN CreateMapping: Inter address: {}, Outer address: {}", interResult, outerResult);
        try {
            if (Main.getMainConfig().getBoolean("stun.availableTest", true)) {
                var testPass = testMapping(interResult, outerResult);
                testPass = false;
                if (!testPass) {
                    stunListener.onNotApplicable(new TranslationComponent(Lang.AUTOSTUN_DOWNLOADER_TUNNEL_TEST_FAILED));
                    return;
                }
            }
            valid.set(true);
            startNATHolder(interResult.getHostString(), interResult.getPort());
            stunListener.onCreate(interResult, outerResult);
        } catch (IOException e) {
            log.error("Failed to test mapping with outer address: {}", outerResult, e);
            stunListener.onClose(e);
        }
    }

    private boolean testMapping(InetSocketAddress interResult, InetSocketAddress outerResult) throws IOException {
        HttpServer httpServer = HttpServer.create(interResult, 0);
        httpServer.createContext("/test", exchange -> {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        httpServer.start();
        // use raw socket to send http request to test mapping
        try (Socket httpSocket = new Socket()) {
            //httpSocket.setSoLinger(true, 0);
            httpSocket.connect(outerResult, 5000);
            String request = "GET /test HTTP/1.1\r\n" +
                    "Host: " + interResult.getHostString() + "\r\n" +
                    "Connection: close\r\n" +
                    "\r\n";
            httpSocket.getOutputStream().write(request.getBytes());
            httpSocket.getOutputStream().flush();
            // Read response
            byte[] buffer = new byte[1024];
            int bytesRead = httpSocket.getInputStream().read(buffer);
            if (bytesRead > 0) {
                String response = new String(buffer, 0, bytesRead);
                log.debug("Received response: {}", response);
                return response.contains("204");
            } else {
                log.warn("No response received from the test mapping request.");
                return false;
            }
        } finally {
            httpServer.stop(0);
        }
    }

    @Override
    public boolean isValid() {
        return valid.get();
    }

    @Override
    public long getLastSuccessHeartbeatAt() {
        return lastSuccessHeartbeatAt;
    }

    @Override
    public long getStartedAt() {
        return startedAt;
    }

    private void startNATHolder(String keepAliveHost, int keepAlivePort) {
        keepAliveService.scheduleAtFixedRate(() -> keepAliveNATTunnel(keepAliveHost, keepAlivePort), 1L, 10L, TimeUnit.SECONDS);
    }

    private void keepAliveNATTunnel(String keepAliveHost, int keepAlivePort) {
        try {
            log.debug("Sending NAT Keep-Alive request from {}:{}", keepAliveHost, keepAlivePort);
            Socket socket = getKeepAliveSocket(keepAliveHost, keepAlivePort);
            socket.getOutputStream().write(("HEAD / HTTP/1.1\r\nHost: qq.com\r\nUser-Agent: PeerBanHelper-NAT-Keeper/1.0\r\nConnection: keep-alive\r\n\r\n").getBytes());
            socket.getOutputStream().flush();
            byte[] buffer = new byte[1024];
            int bytesRead = socket.getInputStream().read(buffer);
            if (bytesRead > 0) {
                String statusLine = new String(buffer, 0, bytesRead);
                log.debug("NAT Keep-Alive request result: {}", statusLine);
            }
            lastSuccessHeartbeatAt = System.currentTimeMillis();
        } catch (IOException e) {
            log.warn("Failed to send NAT Keep-Alive request: {}", e.getMessage());
            if (keepAliveSocket != null) {
                try {
                    keepAliveSocket.close();
                } catch (IOException ex) {
                    // ignore
                }
                keepAliveSocket = null;
            }
        }
    }

    private Socket getKeepAliveSocket(String keepAliveHost, int keepAlivePort) throws IOException {
        if (this.keepAliveSocket != null && this.keepAliveSocket.isConnected() && !this.keepAliveSocket.isClosed()) {
            return keepAliveSocket;
        }
        this.keepAliveSocket = StunSocketTool.getSocket();
        log.debug("Creating keep-alive socket for NAT traversal keep-alive from {}:{}", keepAliveHost, keepAlivePort);
        this.keepAliveSocket.bind(new InetSocketAddress(keepAliveHost, keepAlivePort));
        if (keepAliveSocket.supportedOptions().contains(StandardSocketOptions.SO_REUSEPORT)) {
            keepAliveSocket.setOption(StandardSocketOptions.SO_REUSEPORT, true);
        }
        if (keepAliveSocket.supportedOptions().contains(StandardSocketOptions.SO_REUSEADDR)) {
            keepAliveSocket.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        }
        keepAliveSocket.connect(new InetSocketAddress("qq.com", 80), 1000);
        return keepAliveSocket;
    }


    @Override
    public void close() throws Exception {
        valid.set(false);
        keepAliveService.close();
        stunListener.onClose(null);
        if (keepAliveSocket != null && !keepAliveSocket.isClosed()) {
            try {
                keepAliveSocket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
