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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sockslib.client.SocksProxy;
import sockslib.common.methods.SocksMethod;
import sockslib.common.net.MonitorSocketWrapper;
import sockslib.common.net.NetworkMonitor;
import sockslib.server.listener.PipeInitializer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The class <code>BasicSocksProxyServer</code> is a implementation of {@link SocksProxyServer}
 * .<br>
 * You can build a SOCKS5 server easily by following codes:<br>
 * <pre>
 * ProxyServer proxyServer = new BasicSocksProxyServer(Socks5Handler.class);
 * proxyServer.start(); // Create a SOCKS5 server bind at 1080.
 * </pre>
 * <p>
 * If you want change the port, you can using following codes:
 * </p>
 * <pre>
 * proxyServer.start(9999);
 * </pre>
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Apr 19, 2015 1:10:17 PM
 */
public class BasicSocksProxyServer implements SocksProxyServer, Runnable {

    protected static final Logger logger = LoggerFactory.getLogger(BasicSocksProxyServer.class);

    /**
     * Number of threads in thread pool.
     */
    protected static final int THREAD_NUMBER = 100;

    /**
     * Thread pool used to process each connection.
     */
    private ExecutorService executorService;

    /**
     * Session manager
     */
    private SessionManager sessionManager = new BasicSessionManager();

    /**
     * The next session's ID.
     */
    private long nextSessionId = 0;

    /**
     * Server socket.
     */
    private ServerSocket serverSocket;

    /**
     * SOCKS socket handler class.
     */
    private Class<? extends SocksHandler> socksHandlerClass;

    /**
     * Sessions that server managed.
     */
    private Map<Long, Session> sessions;

    /**
     * A flag.
     */
    private boolean stop = false;

    /**
     * Thread that start the server.
     */
    private Thread thread;

    /**
     * Timeout for a session.
     */
    private int timeout = 10000;

    private boolean daemon = false;

    /**
     * Method selector.
     */
    private MethodSelector methodSelector = new SocksMethodSelector();

    /**
     * Buffer size.
     */
    private int bufferSize = 1024 * 1024 * 5;

    private int bindPort = DEFAULT_SOCKS_PORT;

    private InetAddress bindAddr;

    private SocksProxy proxy;

    private NetworkMonitor networkMonitor = new NetworkMonitor();

    private PipeInitializer pipeInitializer;

    /**
     * Constructs a {@link BasicSocksProxyServer} by a {@link SocksHandler} class. The bind port is
     * 1080.
     *
     * @param socketHandlerClass {@link SocksHandler} class.
     */
    public BasicSocksProxyServer(Class<? extends SocksHandler> socketHandlerClass) {
        this(socketHandlerClass, DEFAULT_SOCKS_PORT, Executors.newVirtualThreadPerTaskExecutor());
    }

    /**
     * Constructs a {@link BasicSocksProxyServer} by a {@link SocksHandler} class and a port.
     *
     * @param socketHandlerClass {@link SocksHandler} class.
     * @param port               The port that SOCKS server will listen.
     */
    public BasicSocksProxyServer(Class<? extends SocksHandler> socketHandlerClass, int port) {
        this(socketHandlerClass, port, Executors.newVirtualThreadPerTaskExecutor());
    }

    /**
     * Constructs a {@link BasicSocksProxyServer} by a {@link SocksHandler} class and a
     * ExecutorService.
     *
     * @param socketHandlerClass {@link SocksHandler} class.
     * @param executorService    Thread pool.
     */
    public BasicSocksProxyServer(Class<? extends SocksHandler> socketHandlerClass, ExecutorService
            executorService) {
        this(socketHandlerClass, DEFAULT_SOCKS_PORT, executorService);
    }

    /**
     * Constructs a {@link BasicSocksProxyServer} by a {@link SocksHandler} class , a port and a
     * ExecutorService.
     *
     * @param socketHandlerClass {@link SocksHandler} class.
     * @param port               The port that SOCKS server will listen.
     * @param executorService    Thread pool.
     */
    public BasicSocksProxyServer(Class<? extends SocksHandler> socketHandlerClass, int port,
                                 ExecutorService executorService) {
        this.socksHandlerClass =
                checkNotNull(socketHandlerClass, "Argument [socksHandlerClass] may not be null");
        this.executorService =
                checkNotNull(executorService, "Argument [executorService] may not be null");
        this.bindPort = port;
        sessions = new HashMap<>();
    }

    @Override
    public void run() {
        logger.info("Start proxy server at port:{}", bindPort);
        while (!stop) {
            try {
                Socket socket = serverSocket.accept();
                socket = processSocketBeforeUse(socket);
                socket.setSoTimeout(timeout);
                Session session = sessionManager.newSession(socket);
                SocksHandler socksHandler = createSocksHandler();
                /* initialize socks handler */
                socksHandler.setSession(session);
                initializeSocksHandler(socksHandler);

                executorService.execute(socksHandler);

            } catch (IOException e) {
                // Catches the exception that cause by shutdown method.
                if (e.getMessage().equals("Socket closed") && stop) {
                    logger.debug("Server shutdown");
                    return;
                }
                logger.debug(e.getMessage(), e);
            }
        }
    }

    @Override
    public void shutdown() {
        stop = true;
        executorService.shutdown();
        if (thread != null) {
            thread.interrupt();
        }
        try {
            closeAllSession();
            if (serverSocket != null && serverSocket.isBound()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public void start() throws IOException {
        serverSocket = createServerSocket(bindPort, bindAddr);
        thread = Thread.ofVirtual().unstarted(this);
        thread.setName("fs-thread");
        //thread.setDaemon(daemon);
        thread.start();
    }

    protected ServerSocket createServerSocket(int bindPort, InetAddress bindAddr) throws IOException {
        return new ServerSocket(bindPort, 50, bindAddr);
    }

    @Override
    public SocksHandler createSocksHandler() {
        try {
            return socksHandlerClass.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    @Override
    public void initializeSocksHandler(SocksHandler socksHandler) {
        socksHandler.setMethodSelector(methodSelector);
        socksHandler.setBufferSize(bufferSize);
        socksHandler.setProxy(proxy);
        socksHandler.setSocksProxyServer(this);
    }

    /**
     * Closes all sessions.
     */
    protected void closeAllSession() {
        for (long key : sessions.keySet()) {
            sessions.get(key).close();
        }

    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    private synchronized long getNextSessionId() {
        nextSessionId++;
        return nextSessionId;
    }

    @Override
    public Map<Long, Session> getManagedSessions() {
        return sessions;
    }

    @Override
    public void setSupportMethods(SocksMethod... methods) {
        methodSelector.setSupportMethod(methods);

    }

    @Override
    public int getTimeout() {
        return timeout;
    }


    @Override
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    @Override
    public int getBufferSize() {
        return bufferSize;
    }

    @Override
    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    @Override
    public SocksProxy getProxy() {
        return proxy;
    }

    @Override
    public void setProxy(SocksProxy proxy) {
        this.proxy = proxy;
    }

    @Override
    public InetAddress getBindAddr() {
        return bindAddr;
    }

    @Override
    public void setBindAddr(InetAddress bindAddr) {
        this.bindAddr = bindAddr;
    }

    @Override
    public int getBindPort() {
        return bindPort;
    }

    @Override
    public void setBindPort(int bindPort) {
        this.bindPort = bindPort;
    }

    @Override
    public boolean isDaemon() {
        return daemon;
    }

    @Override
    public void setDaemon(boolean daemon) {
        this.daemon = daemon;
    }

    public Thread getServerThread() {
        return thread;
    }

    public NetworkMonitor getNetworkMonitor() {
        return networkMonitor;
    }

    public void setNetworkMonitor(NetworkMonitor networkMonitor) {
        this.networkMonitor = checkNotNull(networkMonitor);
    }

    protected Socket processSocketBeforeUse(Socket socket) {
        return new MonitorSocketWrapper(socket, networkMonitor);
    }

    @Override
    public SessionManager getSessionManager() {
        return sessionManager;
    }

    @Override
    public void setSessionManager(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public PipeInitializer getPipeInitializer() {
        return pipeInitializer;
    }

    @Override
    public void setPipeInitializer(PipeInitializer pipeInitializer) {
        this.pipeInitializer = pipeInitializer;
    }
}
