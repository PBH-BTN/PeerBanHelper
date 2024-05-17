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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sockslib.common.*;
import sockslib.common.methods.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

/**
 * The class <code>Socks5</code> has implements SOCKS5 protocol.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Mar 16, 2015 4:57:32 PM
 * @see <a href="http://www.ietf.org/rfc/rfc1928.txt">SOCKS Protocol Version 5</a>
 */
public class Socks5 implements SocksProxy {

    /**
     * Version of SOCKS protocol.
     */
    public static final byte SOCKS_VERSION = 0x05;
    /**
     * Reserved field.
     */
    public static final byte RESERVED = 0x00;
    public static final int REP_SUCCEEDED = 0x00;
    public static final int REP_GENERAL_SOCKS_SERVER_FAILURE = 0x01;
    public static final int REP_CONNECTION_NOT_ALLOWED_BY_RULESET = 0x02;
    public static final int REP_NETWORK_UNREACHABLE = 0x03;
    public static final int REP_HOST_UNREACHABLE = 0x04;
    public static final int REP_CONNECTION_REFUSED = 0x05;
    public static final int REP_TTL_EXPIRED = 0x06;
    public static final int REP_COMMAND_NOT_SUPPORTED = 0x07;
    public static final int REP_ADDRESS_TYPE_NOT_SUPPORTED = 0x08;
    /**
     * Authentication succeeded code.
     */
    public static final byte AUTHENTICATION_SUCCEEDED = 0x00;
    /**
     * Logger.
     */
    protected static final Logger logger = LoggerFactory.getLogger(Socks5.class);
    private SocksProxy chainProxy;
    /**
     * Authentication.
     */
    private Credentials credentials = new AnonymousCredentials();
    /**
     * SOCKS5 server's address. IPv4 or IPv6 address.
     */
    private InetAddress inetAddress;
    /**
     * SOCKS5 server's port;
     */
    private int port = SOCKS_DEFAULT_PORT;
    /**
     * The socket that will connect to SOCKS5 server.
     */
    private Socket proxySocket;
    /**
     * SOCKS5 client acceptable methods.
     */
    private List<SocksMethod> acceptableMethods;
    /**
     * Use to send a request to SOCKS server and receive a method that SOCKS server selected .
     */
    private SocksMethodRequester socksMethodRequester = new GenericSocksMethodRequester();
    /**
     * Use to send command to SOCKS5 sever
     */
    private SocksCommandSender socksCmdSender = new GenericSocksCommandSender();
    /**
     * Resolve remote server's domain name in SOCKS server if it's false. It's default false.
     */
    private boolean alwaysResolveAddressLocally = false;

    /**
     * Constructs a Socks5 instance.
     *
     * @param socketAddress SOCKS5 server's address.
     * @param username      Username of the authentication.
     * @param password      Password of the authentication.
     */
    public Socks5(SocketAddress socketAddress, String username, String password) {
        this(socketAddress);
        setCredentials(new UsernamePasswordCredentials(username, password));
    }

    /**
     * Constructs a Socks5 instance.
     *
     * @param host SOCKS5's server host.
     * @param port SOCKS5's server port.
     * @throws UnknownHostException If the host can't be resolved.
     */
    public Socks5(String host, int port) throws UnknownHostException {
        this(InetAddress.getByName(host), port);
    }

    /**
     * Constructs a Socks5 instance.
     *
     * @param inetAddress SOCKS5 server's address.
     * @param port        SOCKS5 server's port.
     */
    public Socks5(InetAddress inetAddress, int port) {
        this(new InetSocketAddress(inetAddress, port));
    }

    /**
     * Constructs a Socks5 instance with a java.net.SocketAddress instance.
     *
     * @param socketAddress SOCKS5 server's address.
     */
    public Socks5(SocketAddress socketAddress) {
        this(null, socketAddress);
    }

    public Socks5(SocksProxy chainProxy, SocketAddress socketAddress) {
        init();
        if (socketAddress instanceof InetSocketAddress) {
            inetAddress = ((InetSocketAddress) socketAddress).getAddress();
            port = ((InetSocketAddress) socketAddress).getPort();
            this.setChainProxy(chainProxy);
        } else {
            throw new IllegalArgumentException("Only supports java.net.InetSocketAddress");
        }
    }

    /**
     * Constructs a Socks instance.
     *
     * @param host        SOCKS5 server's host.
     * @param port        SOCKS5 server's port.
     * @param credentials credentials.
     * @throws UnknownHostException If the host can't be resolved.
     */
    public Socks5(String host, int port, Credentials credentials) throws UnknownHostException {
        init();
        this.inetAddress = InetAddress.getByName(host);
        this.port = port;
        this.credentials = credentials;
    }

    /**
     * Constructs a Socks5 instance without any parameter.
     */
    private void init() {
        acceptableMethods = new ArrayList<>();
        acceptableMethods.add(new NoAuthenticationRequiredMethod());
        acceptableMethods.add(new GssApiMethod());
        acceptableMethods.add(new UsernamePasswordMethod());
    }

    @Override
    public void buildConnection() throws SocksException, IOException {
        if (inetAddress == null) {
            throw new IllegalArgumentException("Please set inetAddress before calling buildConnection.");
        }
        if (proxySocket == null) {
            proxySocket = createProxySocket(inetAddress, port);
        } else if (!proxySocket.isConnected()) {
            proxySocket.connect(new InetSocketAddress(inetAddress, port));
        }

        SocksMethod method =
                socksMethodRequester.doRequest(acceptableMethods, proxySocket, SOCKS_VERSION);
        method.doMethod(this);
    }

    @Override
    public CommandReplyMessage requestConnect(String host, int port) throws SocksException,
            IOException {
        if (!alwaysResolveAddressLocally) {
            // resolve address in SOCKS server
            return socksCmdSender.send(proxySocket, SocksCommand.CONNECT, host, port, SOCKS_VERSION);

        } else {
            // resolve address in local.
            InetAddress address = InetAddress.getByName(host);
            return socksCmdSender.send(proxySocket, SocksCommand.CONNECT, address, port, SOCKS_VERSION);
        }
    }

    @Override
    public CommandReplyMessage requestConnect(InetAddress address, int port) throws SocksException,
            IOException {
        return socksCmdSender.send(proxySocket, SocksCommand.CONNECT, address, port, SOCKS_VERSION);
    }

    @Override
    public CommandReplyMessage requestConnect(SocketAddress address) throws SocksException,
            IOException {
        return socksCmdSender.send(proxySocket, SocksCommand.CONNECT, address, SOCKS_VERSION);
    }

    @Override
    public CommandReplyMessage requestBind(String host, int port) throws SocksException, IOException {
        return socksCmdSender.send(proxySocket, SocksCommand.BIND, host, port, SOCKS_VERSION);
    }

    @Override
    public CommandReplyMessage requestBind(InetAddress inetAddress, int port) throws
            SocksException, IOException {
        return socksCmdSender.send(proxySocket, SocksCommand.BIND, inetAddress, port, SOCKS_VERSION);
    }

    @Override
    public Socket accept() throws SocksException, IOException {
        CommandReplyMessage messge = socksCmdSender.checkServerReply(proxySocket.getInputStream());
        logger.debug("accept a connection from:{}", messge.getSocketAddress());
        return this.proxySocket;
    }

    @Override
    public CommandReplyMessage requestUdpAssociate(String host, int port) throws SocksException,
            IOException {
        return socksCmdSender.send(proxySocket, SocksCommand.UDP_ASSOCIATE, new InetSocketAddress
                (host, port), SOCKS_VERSION);
    }

    @Override
    public CommandReplyMessage requestUdpAssociate(InetAddress address, int port) throws
            SocksException, IOException {
        return socksCmdSender.send(proxySocket, SocksCommand.UDP_ASSOCIATE, new InetSocketAddress
                (address, port), SOCKS_VERSION);
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public Socks5 setPort(int port) {
        this.port = port;
        return this;
    }

    @Override
    public Socket getProxySocket() {
        return proxySocket;
    }

    @Override
    public Socks5 setProxySocket(Socket proxySocket) {
        this.proxySocket = proxySocket;
        return this;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return proxySocket.getInputStream();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return proxySocket.getOutputStream();
    }

    @Override
    public List<SocksMethod> getAcceptableMethods() {
        return acceptableMethods;
    }

    @Override
    public Socks5 setAcceptableMethods(List<SocksMethod> acceptableMethods) {
        this.acceptableMethods = acceptableMethods;
        SocksMethodRegistry.overWriteRegistry(acceptableMethods);
        return this;
    }

    @Override
    public Credentials getCredentials() {
        return credentials;
    }

    @Override
    public Socks5 setCredentials(Credentials credentials) {
        this.credentials = credentials;
        return this;
    }

    @Override
    public SocksMethodRequester getSocksMethodRequester() {
        return socksMethodRequester;
    }

    @Override
    public Socks5 setSocksMethodRequester(SocksMethodRequester requester) {
        this.socksMethodRequester = requester;
        return this;
    }

    @Override
    public SocksProxy copy() {
        Socks5 socks5 = new Socks5(inetAddress, port);
        socks5.setAcceptableMethods(acceptableMethods).setAlwaysResolveAddressLocally
                (alwaysResolveAddressLocally).setCredentials(credentials).setSocksMethodRequester
                (socksMethodRequester).setChainProxy(chainProxy);
        return socks5;
    }

    @Override
    public SocksProxy copyWithoutChainProxy() {
        return copy().setChainProxy(null);
    }

    @Override
    public int getSocksVersion() {
        return SOCKS_VERSION;
    }

    @Override
    public SocksProxy getChainProxy() {
        return chainProxy;
    }

    @Override
    public SocksProxy setChainProxy(SocksProxy chainProxy) {
        this.chainProxy = chainProxy;
        return this;
    }

    @Override
    public Socks5 setHost(String host) throws UnknownHostException {
        inetAddress = InetAddress.getByName(host);
        return this;
    }

    @Override
    public InetAddress getInetAddress() {
        return inetAddress;
    }

    /**
     * Sets SOCKS5 proxy server's IP address.
     *
     * @param inetAddress IP address of SOCKS5 proxy server.
     * @return The instance of {@link Socks5}.
     */
    public Socks5 setInetAddress(InetAddress inetAddress) {
        this.inetAddress = inetAddress;
        return this;
    }

    @Override
    public String toString() {
        StringBuilder stringBuffer = new StringBuilder("[SOCKS5:");
        stringBuffer.append(new InetSocketAddress(inetAddress, port)).append("]");
        if (getChainProxy() != null) {
            return stringBuffer.append(" --> ").append(getChainProxy().toString()).toString();
        }
        return stringBuffer.toString();
    }

    @Override
    public Socket createProxySocket(InetAddress address, int port) throws IOException {
        return new Socket(address, port);
    }

    @Override
    public Socket createProxySocket() throws IOException {
        return new Socket();
    }

    public boolean isAlwaysResolveAddressLocally() {
        return alwaysResolveAddressLocally;
    }

    public Socks5 setAlwaysResolveAddressLocally(boolean alwaysResolveAddressLocally) {
        this.alwaysResolveAddressLocally = alwaysResolveAddressLocally;
        return this;
    }

}
