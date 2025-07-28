package com.ghostchu.peerbanhelper.util.traversal.stun;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.util.PBHPortMapper;
import com.offbynull.portmapper.mapper.PortMapper;
import com.offbynull.portmapper.mapper.PortType;
import com.sun.net.httpserver.HttpServer;
import lombok.Cleanup;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class StunTcpTunnel implements AutoCloseable {
    private final StunListener stunListener;
    private final ScheduledExecutorService keepAliveService = Executors.newScheduledThreadPool(1, runnable -> Thread.ofVirtual().name("StunTcpTunnel-KeepAlive").unstarted(runnable));
    private final AtomicBoolean valid = new AtomicBoolean(false);
    private final PBHPortMapper pbhPortMapper;
    private final List<PortMapper> portMappers;

    public StunTcpTunnel(PBHPortMapper pbhPortMapper, StunListener stunListener) {
        this.pbhPortMapper = pbhPortMapper;
        this.stunListener = stunListener;
        this.portMappers = pbhPortMapper.getMappers();
    }

    @SneakyThrows
    public void createMapping(int localPort) throws IOException {
        if (localPort == 0) {
            @Cleanup
            var tmpSocket = new ServerSocket(0);
            localPort = tmpSocket.getLocalPort();
            tmpSocket.close();
        }
        pbhPortMapper.mapPort(portMappers, PortType.TCP, localPort).join();
        StunClient stunClient = new StunClient(Main.getMainConfig().getStringList("stun.servers"), Main.getMainConfig().getString("stun.sourceHost"), localPort, false);
        var mappingResult = stunClient.getMapping();
        var interResult = mappingResult.interAddress();
        var outerResult = mappingResult.outerAddress();
        log.debug("Inter address: {}, Outer address: {}", interResult, outerResult);
        try {
            if (Main.getMainConfig().getBoolean("stun.availableTest", true)) {
                var testPass = testMapping(interResult, outerResult);
                if (!testPass) {
                    stunListener.onClose(new IllegalStateException("Available test failed, mapping might not be valid"));
                    return;
                }
            }
            valid.set(true);
            stunListener.onCreate(interResult, outerResult);
        } catch (IOException e) {
            log.error("Failed to test mapping with outer address: {}", outerResult, e);
            stunListener.onClose(e);
        }
    }

    @SneakyThrows
    private boolean testMapping(InetSocketAddress interResult, InetSocketAddress outerResult) throws IOException {
        HttpServer httpServer = HttpServer.create(interResult, 0);
        httpServer.createContext("/test", exchange -> {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        httpServer.start();
        // use raw socket to send http request to test mapping
        try (Socket httpSocket = new Socket()) {
            httpSocket.setSoLinger(true,0);
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

    public boolean isValid() {
        return valid.get();
    }


    @Override
    public void close() throws Exception {
        valid.set(false);
        keepAliveService.close();
        stunListener.onClose(null);
    }
}
