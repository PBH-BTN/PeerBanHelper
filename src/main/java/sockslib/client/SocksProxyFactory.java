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

import com.google.common.base.Strings;
import sockslib.common.Credentials;
import sockslib.common.KeyStoreInfo;
import sockslib.common.SSLConfiguration;
import sockslib.common.UsernamePasswordCredentials;
import sockslib.utils.PathUtil;

import java.io.FileNotFoundException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

/**
 * The class <code>SocksFactory</code> represents a factory that can build {@link SocksProxy}
 * instance.
 *
 * @author Youchao Feng
 * @version 1.0
 * @since 1.0
 */
public class SocksProxyFactory {

    /**
     * Creates a {@link SocksProxy} instance with a string.<br>
     * For example:<br>
     * <ul>
     * <li>host,1080 = {@link Socks5#Socks5(String, int)}</li>
     * <li>host,1080,root,123456 = {@link Socks5#Socks5(String, int, Credentials)}</li>
     * <li>host,1080,root,123456,trustKeyStorePath,trustKeyStorePassword = Creates a
     * {@link SSLSocks5} instance</li>
     * <li>host,1080,root,123456,trustKeyStorePath,trustKeyStorePassword,keyStorePath,
     * keystorePathPassword = Creates a {@link SSLSocks5} instance which supports client
     * authentication</li>
     * </ul>
     *
     * @param value a string.
     * @return a {@link SocksProxy} instance.
     * @throws UnknownHostException  if the host is unknown.
     * @throws FileNotFoundException if file not found.
     */
    public static SocksProxy parse(String value) throws UnknownHostException, FileNotFoundException {
        SocksProxy socks = null;
        String host;
        int port;
        String username;
        String password;
        KeyStoreInfo trustKeyStoreInfo;
        KeyStoreInfo keyStoreInfo;
        SSLConfiguration configuration;

        if (value == null) {
            throw new IllegalArgumentException("Input string can't be null");
        }
        String[] values = value.split(",");
        try {
            switch (values.length) {
                case 2:
                    host = values[0];
                    port = Integer.parseInt(values[1]);
                    return new Socks5(host, port);

                case 4:
                    host = values[0];
                    port = Integer.parseInt(values[1]);
                    username = values[2];
                    password = values[3];
                    if (Strings.isNullOrEmpty(username)) {
                        return new Socks5(host, port);
                    } else {
                        return new Socks5(host, port, new UsernamePasswordCredentials(username, password));
                    }

                case 6:
                    host = values[0];
                    port = Integer.parseInt(values[1]);
                    username = values[2];
                    password = values[3];
                    trustKeyStoreInfo = new KeyStoreInfo(PathUtil.getAbstractPath(values[4]), values[5]);
                    configuration = new SSLConfiguration(null, trustKeyStoreInfo);
                    socks = new SSLSocks5(new InetSocketAddress(host, port), configuration);
                    if (!Strings.isNullOrEmpty(username)) {
                        socks.setCredentials(new UsernamePasswordCredentials(username, password));
                    }
                    return socks;

                case 8:
                    host = values[0];
                    port = Integer.parseInt(values[1]);
                    username = values[2];
                    password = values[3];
                    trustKeyStoreInfo = new KeyStoreInfo(PathUtil.getAbstractPath(values[4]), values[5]);
                    keyStoreInfo = new KeyStoreInfo(PathUtil.getAbstractPath(values[6]), values[7]);
                    configuration = new SSLConfiguration(keyStoreInfo, trustKeyStoreInfo);
                    socks = new SSLSocks5(new InetSocketAddress(host, port), configuration);
                    if (!Strings.isNullOrEmpty(username)) {
                        socks.setCredentials(new UsernamePasswordCredentials(username, password));
                    }
                    return socks;
                default:
                    throw new IllegalArgumentException("The input string should be formatted as [HOST],"
                            + "[IP],[USERNAME],[PASSWORD],[TRUST_KEY_STORE],[TRUST_KEY_STORE_PASSWORD],"
                            + "[KEY_STORE],[KEY_STORE_PASSWORD]");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Port should be a number between 1 and 65535");
        }
    }
}
