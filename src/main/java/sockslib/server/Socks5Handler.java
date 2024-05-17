/*
 * Copyright 2015-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package sockslib.server;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sockslib.client.SocksProxy;
import sockslib.client.SocksSocket;
import sockslib.common.ProtocolErrorException;
import sockslib.common.SocksException;
import sockslib.common.methods.SocksMethod;
import sockslib.server.io.Pipe;
import sockslib.server.io.PipeListener;
import sockslib.server.io.SocketPipe;
import sockslib.server.msg.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;

/**
 * The class <code>Socks5Handler</code> represents a handler that can handle SOCKS5 protocol.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Apr 16, 2015 11:03:49 AM
 */
@Slf4j
public class Socks5Handler implements SocksHandler {

    /**
     * Logger that subclasses also can use.
     */
    protected static final Logger logger = LoggerFactory.getLogger(Socks5Handler.class);

    /**
     * Protocol version.
     */
    private static final int VERSION = 0x5;

    /**
     * Session.
     */
    private Session session;

    /**
     * Method selector.
     */
    private MethodSelector methodSelector;

    private int bufferSize;

    private SocksProxy proxy;

    private SocksProxyServer socksProxyServer;

    private SessionManager sessionManager;

    /**
     * This function starts the pipe and blocks until the pipe stops.
     *
     * @param pipe A pipe that has not yet been started.
     * @throws InterruptedException if this thread is interrupted.
     */
    private static void runPipe(final Pipe pipe) throws InterruptedException {
        StopListener listener = new StopListener();
        pipe.addPipeListener(listener);

        pipe.start(); // This method will build two threads to run two internal pipes.

        // In normal operation, pipe.isRunning() and !listener.wasStopped() should be
        // equal.  However, they can be different if start() failed, or if there is a
        // bug in SocketPipe or StreamPipe.  Checking both ensures that there is no
        // possibility of a hang or busy-loop here.
        while (pipe.isRunning() && !listener.wasStopped()) {
            listener.await();
        }
    }

    /**
     * This function starts the pipe and blocks until the pipe stops.  If the thread is interrupted,
     * it closes the pipe and the session.
     */
    private static void waitForPipe(final Pipe pipe, final Session session) {
        try {
            runPipe(pipe);
        } catch (InterruptedException e) {
            pipe.stop();
            session.close();
            // logger.info("SESSION[{}] closed", session.getId());
        }
    }

    @Override
    public void handle(Session session) throws Exception {
        sessionManager = getSocksProxyServer().getSessionManager();
        sessionManager.sessionOnCreate(session);

        MethodSelectionMessage msg = new MethodSelectionMessage();
        session.read(msg);

        if (msg.getVersion() != VERSION) {
            throw new ProtocolErrorException();
        }
        SocksMethod selectedMethod = methodSelector.select(msg);

        logger.debug("SESSION[{}] Response client:{}", session.getId(), selectedMethod.getMethodName());
        // send select method.
        session.write(new MethodSelectionResponseMessage(VERSION, selectedMethod));

        // do method.
        selectedMethod.doMethod(session);

        CommandMessage commandMessage = new CommandMessage();
        session.read(commandMessage); // Read command request.

//        log.info("SESSION[{}] request:{}  {}:{}", session.getId(), commandMessage.getCommand(),
//            commandMessage.getAddressType() != AddressType.DOMAIN_NAME ?
//                commandMessage.getInetAddress() :
//                commandMessage.getHost(), commandMessage.getPort());

        // If there is a SOCKS exception in command message, It will send a right response to client.
        if (commandMessage.hasSocksException()) {
            ServerReply serverReply = commandMessage.getSocksException().getServerReply();
            session.write(new CommandResponseMessage(serverReply));
            return;
        }

        /**************************** DO COMMAND ******************************************/
        sessionManager.sessionOnCommand(session, commandMessage);
        switch (commandMessage.getCommand()) {
            case BIND:
                doBind(session, commandMessage);
                break;
            case CONNECT:
                doConnect(session, commandMessage);
                break;
            case UDP_ASSOCIATE:
                doUDPAssociate(session, commandMessage);
                break;
        }
    }

    @Override
    public void doConnect(Session session, CommandMessage commandMessage) throws SocksException,
            IOException {

        ServerReply reply = null;
        Socket socket = null;
        InetAddress bindAddress = null;
        int bindPort = 0;
        InetAddress remoteServerAddress = commandMessage.getInetAddress();
        int remoteServerPort = commandMessage.getPort();

        // set default bind address.
        byte[] defaultAddress = {0, 0, 0, 0};
        bindAddress = InetAddress.getByAddress(defaultAddress);
        // DO connect
        try {
            // Connect directly.
            if (proxy == null) {
                socket = new Socket(remoteServerAddress, remoteServerPort);
            } else {
                socket = new SocksSocket(proxy, remoteServerAddress, remoteServerPort);
            }
            bindAddress = socket.getLocalAddress();
            bindPort = socket.getLocalPort();
            reply = ServerReply.SUCCEEDED;
        } catch (IOException e) {
            if (e.getMessage().equals("Connection refused")) {
                reply = ServerReply.CONNECTION_REFUSED;
            } else if (e.getMessage().equals("Operation timed out")) {
                reply = ServerReply.TTL_EXPIRED;
            } else if (e.getMessage().equals("Network is unreachable")) {
                reply = ServerReply.NETWORK_UNREACHABLE;
            } else if (e.getMessage().equals("Connection timed out")) {
                reply = ServerReply.TTL_EXPIRED;
            } else {
                reply = ServerReply.GENERAL_SOCKS_SERVER_FAILURE;
            }
//      logger.info("SESSION[{}] connect {} [{}] exception:{}", session.getId(), new
//          InetSocketAddress(remoteServerAddress, remoteServerPort), reply, e.getMessage());
        }

        CommandResponseMessage responseMessage =
                new CommandResponseMessage(VERSION, reply, bindAddress, bindPort);
        session.write(responseMessage);
        if (reply != ServerReply.SUCCEEDED) { // 如果返回失败信息，则退出该方法。
            session.close();
            return;
        }

        Pipe pipe = new SocketPipe(session.getSocket(), socket);
        pipe.setName("SESSION[" + session.getId() + "]");
        pipe.setBufferSize(bufferSize);
        if (getSocksProxyServer().getPipeInitializer() != null) {
            pipe = getSocksProxyServer().getPipeInitializer().initialize(pipe);
        }

        waitForPipe(pipe, session);
    }

    @Override
    public void doBind(Session session, CommandMessage commandMessage) throws SocksException,
            IOException {

        ServerSocket serverSocket = new ServerSocket(commandMessage.getPort());
        int bindPort = serverSocket.getLocalPort();
        Socket socket = null;
        log.info("Create TCP server bind at {} for session[{}]", serverSocket
                .getLocalSocketAddress(), session.getId());
        session.write(new CommandResponseMessage(VERSION, ServerReply.SUCCEEDED, serverSocket
                .getInetAddress(), bindPort));

        socket = serverSocket.accept();
        session.write(new CommandResponseMessage(VERSION, ServerReply.SUCCEEDED, socket
                .getLocalAddress(), socket.getLocalPort()));

        Pipe pipe = new SocketPipe(session.getSocket(), socket);
        pipe.setBufferSize(bufferSize);

        waitForPipe(pipe, session);
        serverSocket.close();
        // throw new NotImplementException("Not implement BIND command");
    }

    @Override
    public void doUDPAssociate(Session session, CommandMessage commandMessage) throws
            SocksException, IOException {
//    int port;
//    if (commandMessage.getPort() == 0){
//      @Cleanup
//      ServerSocket socket = new ServerSocket(0);
//      port = socket.getLocalPort();
//      log.warn("Re-allocated port {}", port);
//    }else{
//      port = commandMessage.getPort();
//    }
        UDPRelayServer udpRelayServer =
                new UDPRelayServer(((InetSocketAddress) session.getClientAddress()).getAddress(),
                        commandMessage.getPort());
        InetSocketAddress socketAddress = (InetSocketAddress) udpRelayServer.start();
        log.info("Create UDP relay server at[{}] for {}", socketAddress, commandMessage
                .getSocketAddress());
        session.write(new CommandResponseMessage(VERSION, ServerReply.SUCCEEDED, InetAddress
                .getLocalHost(), socketAddress.getPort()));
        try {
            // The client should never send any more data on the control socket, so read() should hang
            // until the client closes the socket (returning -1) or this thread is interrupted (throwing
            // InterruptedIOException).
            int nextByte = session.getInputStream().read();
            if (nextByte != -1) {
                logger.warn("Unexpected data on Session[{}]", session.getId());
            }
        } catch (IOException e) {
            // This is expected on a thread interrupt.
        }
        session.close();
        udpRelayServer.stop();
        logger.debug("UDP relay server for session[{}] is closed", session.getId());
    }

    @Override
    public void setSession(Session session) {
        this.session = session;
    }

    @Override
    public void run() {
        try {
            handle(session);
        } catch (Exception e) {
            sessionManager.sessionOnException(session, e);
            //      logger.error("SESSION[{}]: {}", session.getId(), e.getMessage());
        } finally {
            session.close();
            sessionManager.sessionOnClose(session);
            //      logger.info("SESSION[{}] closed, {}", session.getId(), session.getNetworkMonitor().toString
            //          ());
        }
    }

    @Override
    public MethodSelector getMethodSelector() {
        return methodSelector;
    }

    @Override
    public void setMethodSelector(MethodSelector methodSelector) {
        this.methodSelector = methodSelector;
    }

    @Override
    public int getBufferSize() {
        return bufferSize;
    }

    @Override
    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    public SocksProxy getProxy() {
        return proxy;
    }

    @Override
    public void setProxy(SocksProxy proxy) {
        this.proxy = proxy;
    }

    @Override
    public SocksProxyServer getSocksProxyServer() {
        return socksProxyServer;
    }

    @Override
    public void setSocksProxyServer(SocksProxyServer socksProxyServer) {
        this.socksProxyServer = socksProxyServer;
    }

    /**
     * This PipeListener makes it possible to block until onStop is called.
     */
    private static class StopListener implements PipeListener {
        private final CountDownLatch latch = new CountDownLatch(1);
        private boolean stopped = false;

        /**
         * Blocks until onStop is called.
         *
         * @throws InterruptedException if this thread is interrupted.
         */
        void await() throws InterruptedException {
            latch.await();
        }

        boolean wasStopped() {
            return stopped;
        }

        @Override
        public void onStart(Pipe pipe) {
        }

        @Override
        public void onStop(Pipe pipe) {
            stopped = true;
            latch.countDown();
        }

        @Override
        public void onTransfer(Pipe pipe, byte[] buffer, int bufferLength) {
        }

        @Override
        public void onError(Pipe pipe, Exception exception) {
        }
    }

}
