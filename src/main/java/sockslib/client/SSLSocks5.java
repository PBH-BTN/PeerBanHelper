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

package sockslib.client;

import sockslib.common.SSLConfiguration;
import sockslib.common.SSLConfigurationException;
import sockslib.common.SocksException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;

/**
 * The class <code>SSLSocks5</code> represents a SSL based SOCKS5 proxy. It will build a SSL based
 * connection between the client and SOCKS5 server.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date May 18, 2015 1:00:18 PM
 * @see Socks5
 */
public class SSLSocks5 extends Socks5 {

    /**
     * SSL configuration.
     */
    private SSLConfiguration configuration;

    public SSLSocks5(SocketAddress address, SSLConfiguration configuration) {
        super(address);
        this.configuration = configuration;
    }

    public SSLSocks5(InetAddress address, int port, SSLConfiguration configuration) {
        super(address, port);
        this.configuration = configuration;
    }

    @Override
    public Socket createProxySocket(InetAddress address, int port) throws IOException {
        try {
            return configuration.getSSLSocketFactory().createSocket(address, port);
        } catch (SSLConfigurationException e) {
            throw new SocksException(e.getMessage());
        }
    }


    @Override
    public Socket createProxySocket() throws IOException {
        try {
            return configuration.getSSLSocketFactory().createSocket();
        } catch (SSLConfigurationException e) {
            throw new SocksException(e.getMessage());
        }
    }

    @Override
    public SocksProxy copy() {
        return copyWithoutChainProxy().setChainProxy(getChainProxy());
    }

    @Override
    public SocksProxy copyWithoutChainProxy() {
        SSLSocks5 socks5 = new SSLSocks5(getInetAddress(), getPort(), configuration);
        socks5.setAcceptableMethods(getAcceptableMethods()).setAlwaysResolveAddressLocally
                (isAlwaysResolveAddressLocally()).setCredentials(getCredentials()).setInetAddress
                (getInetAddress()).setPort(getPort()).setSocksMethodRequester(getSocksMethodRequester());
        return socks5;
    }

    public SSLConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(SSLConfiguration configuration) {
        this.configuration = configuration;
    }
}
