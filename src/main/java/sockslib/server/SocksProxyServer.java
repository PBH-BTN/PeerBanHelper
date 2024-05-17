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

import sockslib.client.SocksProxy;
import sockslib.common.methods.SocksMethod;
import sockslib.server.listener.PipeInitializer;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * The interface <code>SocksProxyServer</code> represents a SOCKS server.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Mar 25, 2015 10:07:29 AM
 */
public interface SocksProxyServer {

    /**
     * SOCKS server default port.
     */
    int DEFAULT_SOCKS_PORT = 1080;

    /**
     * Starts a SOCKS server.
     *
     * @throws IOException If any I/O error occurs.
     */
    void start() throws IOException;

    /**
     * Shutdown a SOCKS server.
     */
    void shutdown();

    /**
     * Create an instance {@link SocksHandler}.
     *
     * @return Instance of {@link SocksHandler}.
     */
    SocksHandler createSocksHandler();

    /**
     * Initializes {@link SocksHandler}.
     *
     * @param socksHandler The instance of {@link SocksHandler}.
     */
    void initializeSocksHandler(SocksHandler socksHandler);

    /**
     * Sets the methods that socks server supports.
     *
     * @param methods The methods that SOCKS server sports.
     */
    void setSupportMethods(SocksMethod... methods);

    /**
     * Gets all sessions that SOCKS server managed.
     *
     * @return All sessions that SOCKS server managed.
     */
    Map<Long, Session> getManagedSessions();

    /**
     * Returns buffer size.
     *
     * @return Buffer size.
     */
    int getBufferSize();

    /**
     * Sets buffer size.
     *
     * @param bufferSize Buffer size.
     */
    void setBufferSize(int bufferSize);

    /**
     * Returns timeout.
     *
     * @return Timeout.
     */
    int getTimeout();

    /**
     * Sets timeout.
     *
     * @param timeout timeout.
     */
    void setTimeout(int timeout);

    /**
     * Returns server's proxy.
     *
     * @return Server's proxy.
     */
    SocksProxy getProxy();

    /**
     * Set server proxy.
     *
     * @param proxy Proxy server will use.
     */
    void setProxy(SocksProxy proxy);

    /**
     * Sets thread pool.
     *
     * @param executeService Thread pool.
     */
    void setExecutorService(ExecutorService executeService);

    /**
     * Returns server bind addr.
     *
     * @return Server bind addr.
     */
    InetAddress getBindAddr();

    /**
     * Sets server bind addr
     *
     * @param bindAddr Bind addr.
     */
    void setBindAddr(InetAddress bindAddr);

    /**
     * Returns server bind port.
     *
     * @return Server bind port.
     */
    int getBindPort();

    /**
     * Sets server bind port
     *
     * @param bindPort Bind port.
     */
    void setBindPort(int bindPort);

    /**
     * Returns <code>true</code> if the server run in daemon thread.
     *
     * @return <code>true</code> if the server run in daemon thread.
     */
    boolean isDaemon();

    /**
     * Sets <code>true</code> the server will run in daemon thread.
     *
     * @param daemon Daemon thread
     */
    void setDaemon(boolean daemon);

    /**
     * Returns {@link SessionManager}
     *
     * @return {@link SessionManager}
     */
    SessionManager getSessionManager();

    /**
     * Sets {@link SessionManager}
     *
     * @param sessionManager {@link SessionManager}
     */
    void setSessionManager(SessionManager sessionManager);

    PipeInitializer getPipeInitializer();

    void setPipeInitializer(PipeInitializer pipeInitializer);
}
