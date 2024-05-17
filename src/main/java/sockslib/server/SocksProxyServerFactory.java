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

import sockslib.common.methods.NoAuthenticationRequiredMethod;
import sockslib.common.methods.UsernamePasswordMethod;
import sockslib.server.manager.User;

/**
 * The class <code>SocksProxyServerFactory</code> is a factory class for socks server.
 *
 * @author Youchao Feng
 * @version 1.0
 * @since 1.0
 */
public class SocksProxyServerFactory {

    /**
     * Default timeout.
     */
    private static final int TIMEOUT = 100000;

    /**
     * Default buffer size;
     */
    private static final int BUFFER_SIZE = 1024 * 1024 * 5;

    private static final int DEFAULT_PORT = 1080;

    /**
     * Creates a no authentication SOCKS5 server. The server will listen at port 1080.
     *
     * @return The instance of {@link SocksProxyServer} that supports SOCKS5 protocol.
     */
    public static SocksProxyServer newNoAuthenticationServer() {
        return newNoAuthenticationServer(DEFAULT_PORT);
    }

    /**
     * Creates a no authentication SOCKS5 server.
     *
     * @param port The port that SOCKS5 Server will listen.
     * @return The instance of {@link SocksProxyServer} that supports SOCKS5 protocol.
     */
    public static SocksProxyServer newNoAuthenticationServer(int port) {
        SocksProxyServer proxyServer = new BasicSocksProxyServer(Socks5Handler.class, port);
        proxyServer.setBufferSize(BUFFER_SIZE);
        proxyServer.setTimeout(TIMEOUT);
        proxyServer.setSupportMethods(new NoAuthenticationRequiredMethod());
        return proxyServer;
    }

    /**
     * Create a USERNAME/PASSWORD authentication SOCKS5 server.
     *
     * @param users Users.
     * @return The instance of {@link SocksProxyServer} that supports SOCKS5 protocol.
     */
    public static SocksProxyServer newUsernamePasswordAuthenticationServer(User... users) {
        return newUsernamePasswordAuthenticationServer(DEFAULT_PORT, users);
    }

    public static SocksProxyServer newUsernamePasswordAuthenticationServer(int port, User... users) {
        SocksProxyServer proxyServer = new BasicSocksProxyServer(Socks5Handler.class, port);
        proxyServer.setBufferSize(BUFFER_SIZE);
        proxyServer.setTimeout(TIMEOUT);
        UsernamePasswordAuthenticator authenticator = new UsernamePasswordAuthenticator();
        for (User user : users) {
            authenticator.addUser(user.getUsername(), user.getPassword());
        }
        proxyServer.setSupportMethods(new UsernamePasswordMethod(authenticator));
        return proxyServer;
    }

}
