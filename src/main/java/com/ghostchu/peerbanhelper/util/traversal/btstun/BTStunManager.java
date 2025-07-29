package com.ghostchu.peerbanhelper.util.traversal.btstun;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.util.PBHPortMapper;
import com.ghostchu.peerbanhelper.util.traversal.stun.NatDetectionResult;
import com.ghostchu.peerbanhelper.util.traversal.stun.NatType;
import com.ghostchu.peerbanhelper.util.traversal.stun.StunClient;
import com.offbynull.portmapper.mapper.MappedPort;
import com.offbynull.portmapper.mapper.PortType;
import com.sun.net.httpserver.HttpServer;
import lombok.Cleanup;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

@Slf4j
@Component
public class BTStunManager {
    private final Map<Downloader, BTStunInstance> perDownloaderStun = Collections.synchronizedMap(new HashMap<>());
    private final PBHPortMapper portMapper;

    @Getter
    private NatDetectionResult natDetectionResult = new NatDetectionResult(
            NatType.UNKNOWN,
            new InetSocketAddress("0.0.0.0", 0),
            new InetSocketAddress("0.0.0.0", 0),
            new InetSocketAddress("0.0.0.0", 0),
            false,
            "N/A"
    );

    public BTStunManager(PBHPortMapper portMapper) {
        this.portMapper = portMapper;
        refreshNatDetectionResult();
    }

    private CompletableFuture<NatDetectionResult> refreshNatDetectionResult() {
        return CompletableFuture.supplyAsync(() -> {
            StunClient stunClient = new StunClient(Main.getMainConfig().getStringList("stun.servers"), "0.0.0.0", 0, false);
            return stunClient.detectNatType();
        }, Executors.newVirtualThreadPerTaskExecutor());
    }


    private NetworkReachability testNetworkReachability() throws IOException {
        StunClient stunClient = new StunClient(Main.getMainConfig().getStringList("stun.servers"), "0.0.0.0", 0, false);
        @Cleanup
        var tmpSocket = new ServerSocket(0);
        int serverListenPort = tmpSocket.getLocalPort();
        tmpSocket.close();
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("0.0.0.0", serverListenPort), 0);
        httpServer.createContext("/test", exchange -> {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        httpServer.start();
        var publicIp = stunClient.getMapping().outerAddress().getHostString();
        if (testFor(new InetSocketAddress(publicIp, serverListenPort))) {
            return NetworkReachability.RAW_INTERNET;
        }
        MappedPort mappedPort = null;
        try {
            mappedPort = portMapper.mapPort(portMapper.getMappers(), PortType.TCP, serverListenPort).join();
            if (testFor(new InetSocketAddress(publicIp, serverListenPort))) {
                return NetworkReachability.IGD_OPEN;
            }
            var natType = stunClient.detectNatType();
            if (natType.isFullCone()) {
                return NetworkReachability.FULLCONE_HOLE_PUNCH_OPEN;
            }
            if (!natType.isSymmetric()) {
                return NetworkReachability.DUALSIDE_HOLE_PUNCH_OPEN;
            }
            return NetworkReachability.ONLY_OUTGOING_CONNECTION;
        } finally {
            if (mappedPort != null) {
                portMapper.unmapPort(portMapper.getMappers(), mappedPort).thenAccept((ignored) -> {
                });
            }
        }
    }

    public boolean testFor(InetSocketAddress address) {
        // use raw socket to send http request to test mapping
        try (Socket httpSocket = new Socket()) {
            httpSocket.setSoLinger(true, 0);
            httpSocket.connect(address, 5000);
            String request = "GET /test HTTP/1.1\r\n" +
                    "Host: 127.0.0.1" + "\r\n" +
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
        } catch (Exception e) {
            return false;
        }
    }


    enum NetworkReachability {
        RAW_INTERNET,
        IGD_OPEN,
        FULLCONE_HOLE_PUNCH_OPEN,
        DUALSIDE_HOLE_PUNCH_OPEN,
        ONLY_OUTGOING_CONNECTION
    }

}
