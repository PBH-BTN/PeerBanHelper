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

import sockslib.common.AnonymousCredentials;
import sockslib.common.Credentials;
import sockslib.common.SocksException;
import sockslib.common.UsernamePasswordCredentials;
import sockslib.common.methods.SocksMethod;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * The interface <code>SocksProxy</code> define a SOCKS proxy. it's will be used by
 * {@link SocksSocket} or {@link Socks5DatagramSocket}
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Mar 18, 2015 3:29:18 PM
 */
public interface SocksProxy {

    /**
     * Default SOCKS server port.
     */
    int SOCKS_DEFAULT_PORT = 1080;

    /**
     * Get the socket which connect SOCKS server.
     *
     * @return java.net.Socket.
     */
    Socket getProxySocket();

    /**
     * Set a unconnected socket which will be used to connect SOCKS server.
     *
     * @param socket a unconnected socket.
     * @return instance of SocksProxy.
     */
    SocksProxy setProxySocket(Socket socket);

    /**
     * Get SOCKS Server port.
     *
     * @return server port.
     */
    int getPort();

    /**
     * Set SOCKS server port.
     *
     * @param port SOCKS server's port.
     * @return instance of SocksProxy.
     */
    SocksProxy setPort(int port);

    /**
     * Get SOCKS server's address as IPv4 or IPv6.
     *
     * @return server's IP address.
     */
    InetAddress getInetAddress();

    /**
     * Set SOCKS server's host.
     *
     * @param host SOCKS server's host.
     * @return instance of SocksProxy.
     * @throws UnknownHostException if host can't resolve to {@link InetAddress}
     */
    SocksProxy setHost(String host) throws UnknownHostException;

    /**
     * Connect SOCKS server using SOCKS protocol. This method will ask SOCKS server to select
     * a method from the methods listed by client. If SOCKS server need authentication, it will
     * do authentication. If SOCKS server select 0xFF,It means that none of the methods listed by the
     * client are acceptable and this method should throw {@link SocksException}.
     *
     * @throws IOException    if any IO error occurs.
     * @throws SocksException if any error about SOCKS protocol occurs.
     */
    void buildConnection() throws IOException, SocksException;

    /**
     * This method will send a CONNECT command to SOCKS server and ask SOCKS server to connect remote
     * server.
     *
     * @param host Remote server's host.
     * @param port Remote server's port.
     * @return The message that reply by SOCKS server.
     * @throws SocksException If any error about SOCKS protocol occurs.
     * @throws IOException    if any I/O error occurs.
     */
    CommandReplyMessage requestConnect(String host, int port) throws SocksException, IOException;

    /**
     * This method will send a CONNECT command to SOCKS server and ask SOCKS server to connect remote
     * server.
     *
     * @param address Remote server's address as java.net.InetAddress instance.
     * @param port    Remote server's port.
     * @return The message that reply by SOCKS server.
     * @throws SocksException If any error about SOCKS protocol occurs.
     * @throws IOException    If any I/O error occurs.
     */
    CommandReplyMessage requestConnect(InetAddress address, int port) throws SocksException,
            IOException;

    /**
     * This method will send a CONNECT command to SOCKS server and ask SOCKS server to connect remote
     * server.
     *
     * @param address Remote server's address as java.net.SocketAddress instance.
     * @return The message that reply by SOCKS server.
     * @throws SocksException If any error about SOCKS protocol occurs.
     * @throws IOException    If any I/O error occurs.
     */
    CommandReplyMessage requestConnect(SocketAddress address) throws SocksException, IOException;

    /**
     * This method will send a BIND command to SOKCS server.
     *
     * @param host Remote server's host.
     * @param port Remote server's port.
     * @return The message that reply by SOCKS server.
     * @throws SocksException If any error about SOCKS protocol occurs.
     * @throws IOException    If any I/O error occurs.
     */
    CommandReplyMessage requestBind(String host, int port) throws SocksException, IOException;

    /**
     * This method will send a BIND command to SOKCS server.
     *
     * @param inetAddress Remote server's IP address.
     * @param port        Remote server's port.
     * @return The message that reply by SOCKS server.
     * @throws SocksException If any error about SOCKS protocol occurs.
     * @throws IOException    If any I/O error occurs.
     */
    CommandReplyMessage requestBind(InetAddress inetAddress, int port) throws
            SocksException, IOException;

    /**
     * When server has income connection, this method will read second response message from
     * SOCKS server. <br>
     * This method will be blocked if there is no income connection. When there is a income
     * connection, this method will return a socket that looks like connect the remote host.
     *
     * @return Socket that connect the remote host.
     * @throws SocksException If any error about SOCKS protocol occurs.
     * @throws IOException    If any I/O error occurs.
     */
    Socket accept() throws SocksException, IOException;

    /**
     * This method will send a UDP ASSOCIATE command to SOCKS server and ask SOCKS server to establish
     * a relay server.
     *
     * @param host Remote UDP server's host.
     * @param port Remote UDP server's port.
     * @return The message that reply by SOCKS server.
     * @throws SocksException If any error about SOCKS protocol occurs.
     * @throws IOException    If any I/O error occurs.
     */
    CommandReplyMessage requestUdpAssociate(String host, int port) throws SocksException, IOException;

    /**
     * This method will send a UDP ASSOCIATE command to SOCKS server and ask SOCKS server to establish
     * a relay server.
     *
     * @param address Remote UDP server's address.
     * @param port    Remote UDP server's port.
     * @return The message that reply by SOCKS server.
     * @throws SocksException If any error about SOCKS protocol occurs.
     * @throws IOException    If any I/O error occurs.
     */
    CommandReplyMessage requestUdpAssociate(InetAddress address, int port) throws SocksException,
            IOException;

    /**
     * Gets InputStream from the socket that connected SOCKS server.
     *
     * @return java.net.InputStream.
     * @throws IOException if any I/O error occurs.
     */
    InputStream getInputStream() throws IOException;

    /**
     * Gets OutputStream from the socket that connected SOCKS server.
     *
     * @return java.net.OutputStream.
     * @throws IOException if any I/O error occurs.
     */
    OutputStream getOutputStream() throws IOException;

    /**
     * Gets credentials from the SocksProxy.
     *
     * @return {@link Credentials} instance.
     */
    Credentials getCredentials();

    /**
     * Sets credentials.
     *
     * @param credentials {@link Credentials} instance.
     * @return instance of SocksProxy.
     * @see UsernamePasswordCredentials
     * @see AnonymousCredentials
     */
    SocksProxy setCredentials(Credentials credentials);

    /**
     * Gets client's acceptable methods.
     *
     * @return client's acceptable methods.
     */
    List<SocksMethod> getAcceptableMethods();

    /**
     * Sets client's acceptable methods.
     *
     * @param methods methods.
     * @return instance of SocksProxy.
     */
    SocksProxy setAcceptableMethods(List<SocksMethod> methods);

    /**
     * Gets {@link SocksMethodRequester}.
     *
     * @return {@link SocksMethodRequester}.
     */
    SocksMethodRequester getSocksMethodRequester();

    /**
     * Sets {@link SocksMethodRequester}.
     *
     * @param requester {@link SocksMethodRequester}
     * @return instance of SocksProxy.
     */
    SocksProxy setSocksMethodRequester(SocksMethodRequester requester);

    /**
     * Gets version of SOCKS protocol.
     *
     * @return Version of SOCKS protocol.
     */
    int getSocksVersion();

    /**
     * This method can build a same SocksProxy instance. The new instance created by this method has
     * the same properties with the original instance, but they have different socket instance. The
     * new instance's socket is also unconnected.
     *
     * @return The copy of this SocksProxy.
     */
    SocksProxy copy();

    /**
     * Copy the {@link SocksProxy}. It will copy all properties of the {@link SocksProxy} but without
     * chain proxy.
     *
     * @return The copy of this SocksProxy but without chain proxy.
     */
    SocksProxy copyWithoutChainProxy();

    /**
     * Returns the chain proxy.
     *
     * @return the chain proxy.
     */
    SocksProxy getChainProxy();

    /**
     * Returns the instance of <code>SocksProxy</code>.
     *
     * @param chainProxy chain proxy.
     * @return the instance of <code>SocksProxy</code>.
     */
    SocksProxy setChainProxy(SocksProxy chainProxy);

    /**
     * Creates a proxy socket.
     *
     * @param address address.
     * @param port    port.
     * @return Socket instance.
     * @throws IOException If an I\O error occurred.
     */
    Socket createProxySocket(InetAddress address, int port) throws IOException;

    /**
     * Creates a unconnected socket.
     *
     * @return a unconnected socket.
     * @throws IOException If an I\O error occurred.
     */
    Socket createProxySocket() throws IOException;

}
