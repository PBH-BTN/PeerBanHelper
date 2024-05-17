package com.ghostchu.peerbanhelper.proxy;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import lombok.extern.slf4j.Slf4j;
import sockslib.common.SocksCommand;
import sockslib.server.Session;
import sockslib.server.SocksProxyServer;
import sockslib.server.SocksServerBuilder;
import sockslib.server.listener.CloseSessionException;
import sockslib.server.listener.SessionListener;
import sockslib.server.manager.MemoryBasedUserManager;
import sockslib.server.msg.CommandMessage;

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.Executors;

@Slf4j
public class PBHSocks5Server implements AutoCloseable {

    private final PBHSessionManager sessionManager;
    private final PeerBanHelperServer server;
    private SocksProxyServer proxyServer;

    public PBHSocks5Server(PeerBanHelperServer server) {
        this.server = server;
        this.sessionManager = new PBHSessionManager();
        setupListeners();
        try {
            start(InetAddress.getByAddress(new byte[]{0, 0, 0, 0}), 6467, "test", "test");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void setupListeners() {
        sessionManager.addSessionListener("DEBUG", new SessionListener() {
            @Override
            public void onCommand(Session session, CommandMessage message) throws CloseSessionException {
                if (message.getCommand() == SocksCommand.UDP_ASSOCIATE) {
                    log.info("[COMMAND] Session={}, Command={}, Address={}", session, message.getCommand().name(), message.getInetAddress());
                }
            }

            @Override
            public void onException(Session session, Exception exception) {
                //log.error("[EXCEPTION] Session={}, Exception={}", session, exception);
            }

            @Override
            public void onClose(Session session) {
                if (session.getNetworkMonitor().getReceiveUDP() != 0) {
                    log.info("[CLOSE] Session={}, NetworkMonitor={}", session, session.getNetworkMonitor());
                }
            }

            @Override
            public void onCreate(Session session) throws CloseSessionException {
            }
        });
    }

    public void start(InetAddress bind, int port, String username, String password) throws IOException {
        MemoryBasedUserManager userManager = new MemoryBasedUserManager();
        userManager.addUser(username, password);
        this.proxyServer = SocksServerBuilder.newSocks5ServerBuilder()
                .setBindPort(port)
                .setBindAddr(bind)
                .setExecutorService(Executors.newWorkStealingPool(150))
                .setUserManager(userManager)
                .setSessionManager(sessionManager)
                .build();
        proxyServer.setDaemon(true);
        proxyServer.start();
    }

    @Override
    public void close() throws Exception {
        proxyServer.shutdown();
    }
}
