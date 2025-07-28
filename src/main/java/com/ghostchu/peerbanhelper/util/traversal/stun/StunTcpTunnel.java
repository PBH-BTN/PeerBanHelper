package com.ghostchu.peerbanhelper.util.traversal.stun;

import com.ghostchu.peerbanhelper.util.PBHPortMapper;
import com.offbynull.portmapper.mapper.PortMapper;
import com.offbynull.portmapper.mapper.PortType;
import com.sun.net.httpserver.HttpServer;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class StunTcpTunnel implements AutoCloseable {
    private static final List<String> STUN_SERVERS = List.of(
            "turn.cloudflare.com:3478",
            "stun.nextcloud.com:3478",
            "stun.sipnet.com:3478"
    );
    private final OkHttpClient okHttpClient;
    private final StunListener stunListener;
    private final ScheduledExecutorService keepAliveService = Executors.newScheduledThreadPool(1, runnable -> Thread.ofVirtual().name("StunTcpTunnel-KeepAlive").unstarted(runnable));
    private final AtomicBoolean valid = new AtomicBoolean(false);
    private final PBHPortMapper pbhPortMapper;
    private final List<PortMapper> portMappers;

    public StunTcpTunnel(PBHPortMapper pbhPortMapper, StunListener stunListener) {
        this.pbhPortMapper = pbhPortMapper;
        this.stunListener = stunListener;
        this.okHttpClient = new OkHttpClient.Builder()
                .dispatcher(new Dispatcher(Executors.newVirtualThreadPerTaskExecutor()))
                .callTimeout(5, TimeUnit.SECONDS)
                .fastFallback(false)
                .retryOnConnectionFailure(false)
                .build();
        this.portMappers = pbhPortMapper.getMappers();
    }

    public void createMapping(int localPort) throws IOException {
        if(localPort == 0){
            @Cleanup
            var tmpSocket = new ServerSocket(0);
            localPort = tmpSocket.getLocalPort();
            tmpSocket.close();
        }
        pbhPortMapper.mapPort(portMappers , PortType.TCP, localPort).join();
        StunClient stunClient = new StunClient(STUN_SERVERS, "0.0.0.0", localPort, false);
        var mappingResult = stunClient.getMapping();
        var interResult = mappingResult.interAddress();
        var outerResult = mappingResult.outerAddress();
        log.debug("Inter address: {}, Outer address: {}", interResult, outerResult);
        sendPunchTcpRequest(interResult);
        try {
            var testPass = testMapping(interResult, outerResult);
            if (testPass) {
                registerKeepAliveTask(interResult, outerResult);
                valid.set(true);
                stunListener.onCreate(interResult, outerResult);
            } else {
                stunListener.onClose(null);
            }
        } catch (IOException e) {
            log.error("Failed to test mapping with outer address: {}", outerResult, e);
            stunListener.onClose(e);
        }
    }

//    private void registerKeepAliveTask(InetSocketAddress interResult, InetSocketAddress outerResult) {
//        keepAliveService.scheduleWithFixedDelay(() -> {
//            Socket socket = new Socket();
//            try {
//                socket.connect(outerResult, 5000);
//                socket.close();
//            } catch (IOException e) {
//                log.debug("Keep-Alive request invalid, tunnel closed.", e);
//                valid.set(false);
//                stunListener.onClose(e);
//                try {
//                    close();
//                } catch (Exception ignored) {
//                }
//            }
//        }, 0L, 10L, TimeUnit.SECONDS);
//    }

    private void registerKeepAliveTask(InetSocketAddress interResult, InetSocketAddress outerResult) {
        keepAliveService.scheduleWithFixedDelay(() -> {
            try (Socket socket = new Socket()) {
                socket.setReuseAddress(true);
                socket.setSoTimeout(5000);
                // 绑定到内部地址
                socket.bind(interResult);
                // 连接到一个公网地址，保持 NAT 映射活跃
                socket.connect(new InetSocketAddress("1.1.1.1", 53), 5000);
                log.debug("NAT tunnel keep-alive sent from {} to maintain mapping", interResult);
            } catch (IOException e) {
                log.debug("Keep-Alive request failed, tunnel may be closed: {}", e.getMessage());
                valid.set(false);
                stunListener.onClose(e);
                try {
                    close();
                } catch (Exception ignored) {
                }
            }
        }, 0L, 4L, TimeUnit.MINUTES); // 每4分钟发送一次
    }

    private boolean testMapping(InetSocketAddress interResult, InetSocketAddress outerResult) throws IOException {
        HttpServer httpServer = HttpServer.create(interResult, 0);
        httpServer.createContext("/test", exchange -> {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        httpServer.start();
        @SuppressWarnings("HttpUrlsUsage")
        Request request = new Request.Builder()
                .url("http://" + outerResult.getHostString() + ":" + outerResult.getPort() + "/test")
                .get()
                .build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            return response.isSuccessful();
        } finally {
            httpServer.stop(0);
        }
    }

    private void sendPunchTcpRequest(InetSocketAddress localAddress) {
        try (Socket holePunchSocket = new Socket()) {
            holePunchSocket.setReuseAddress(true);
            holePunchSocket.bind(localAddress);
            holePunchSocket.connect(new InetSocketAddress("qq.com", 80), 5000);
            String request = """
                    GET /pbh-stun-initialize HTTP/1.1
                    Host: qq.com
                    Connection: keep-alive
                    User-Agent: PeerBanHelper-STUN-Initializer/1.0
                    """;
            holePunchSocket.getOutputStream().write(request.getBytes());
            holePunchSocket.getOutputStream().flush();
        } catch (IOException e) {
            log.error("Failed to send outgoing request from local address {} to punch hole", localAddress, e);
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
