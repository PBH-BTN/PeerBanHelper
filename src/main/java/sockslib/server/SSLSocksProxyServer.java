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

import sockslib.common.SSLConfiguration;
import sockslib.common.SocksException;

import javax.net.ssl.SSLServerSocket;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * The class <code>SSLSocksProxyServer</code> represents a SSL based SOCKS proxy server.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date May 17, 2015 4:08:06 PM
 */
public class SSLSocksProxyServer extends BasicSocksProxyServer {

    private SSLConfiguration configuration;

    public SSLSocksProxyServer(Class<? extends SocksHandler> socketHandlerClass, ExecutorService
            executorService, SSLConfiguration configuration) {
        this(socketHandlerClass, DEFAULT_SOCKS_PORT, executorService, configuration);
    }

    public SSLSocksProxyServer(Class<? extends SocksHandler> socketHandlerClass, int port,
                               ExecutorService executorService, SSLConfiguration configuration) {
        super(socketHandlerClass, port, executorService);
        this.configuration = configuration;
    }

    public SSLSocksProxyServer(Class<? extends SocksHandler> socketHandlerClass, int port,
                               SSLConfiguration configuration) {
        this(socketHandlerClass, port, Executors.newFixedThreadPool(THREAD_NUMBER), configuration);
    }

    public SSLSocksProxyServer(Class<? extends SocksHandler> socketHandlerClass, SSLConfiguration
            configuration) {
        this(socketHandlerClass, DEFAULT_SOCKS_PORT, Executors.newFixedThreadPool(THREAD_NUMBER),
                configuration);
    }

    @Override
    protected ServerSocket createServerSocket(int bindPort, InetAddress bindAddr) throws IOException {
        try {
            return createSSLServer(bindPort, bindAddr);
        } catch (Exception e) {
            throw new SocksException(e.getMessage());
        }
    }

    public SSLConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(SSLConfiguration configuration) {
        this.configuration = configuration;
    }

    public ServerSocket createSSLServer(int port, InetAddress bindAddr) throws Exception {
        SSLServerSocket serverSocket =
                (SSLServerSocket) configuration.getSSLServerSocketFactory().createServerSocket(port, 50, bindAddr);
        if (configuration.isNeedClientAuth()) {
            serverSocket.setNeedClientAuth(true);
        } else {
            serverSocket.setNeedClientAuth(false);
        }
        return serverSocket;
    }

}
