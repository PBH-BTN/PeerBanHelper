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
import sockslib.common.SocksException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The class <code>SocksSocket</code> is proxy class that help developers use {@link SocksProxy} as
 * same as a java.net.Socket.<br>
 * For example:<br>
 * <pre>
 * SocksProxy proxy = new Socks5(new InetSocketAddress(&quot;127.0.0.1&quot;, 1080));
 * // Setting proxy...
 * Socket socket = new SocksSocket(proxy, new InetSocketAddress(&quot;whois.internic.net&quot;,
 * 43));
 * InputStream inputStream = socket.getInputStream();
 * OutputStream outStream = socket.getOutputStream();
 * // Just use the socket as normal java.net.Socket now.
 * </pre>
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Mar 18, 2015 5:02:31 PM
 */
public class SocksSocket extends Socket {

    protected static final Logger logger = LoggerFactory.getLogger(SocksSocket.class);

    private SocksProxy proxy;

    private String remoteServerHost;

    private int remoteServerPort;

    /**
     * Socket that will connect to SOCKS server.
     */
    private Socket proxySocket;

    /**
     * Create a socket and connect SOCKS Server.
     *
     * @param proxy            Socks proxy.
     * @param remoteServerHost Remote sever host.
     * @param remoteServerPort Remote server port.
     * @throws SocksException If any errors about SOCKS protocol occurred.
     * @throws IOException    If any IO errors occurred.
     */
    public SocksSocket(SocksProxy proxy, String remoteServerHost, int remoteServerPort) throws
            SocksException, IOException {
        this.proxy = checkNotNull(proxy, "Argument [proxy] may not be null").copy();
        this.proxy.setProxySocket(proxySocket);
        this.remoteServerHost =
                checkNotNull(remoteServerHost, "Argument [remoteServerHost] may not be null");
        this.remoteServerPort = remoteServerPort;
        this.proxy.buildConnection();
        proxySocket = this.proxy.getProxySocket();
        initProxyChain();
        this.proxy.requestConnect(remoteServerHost, remoteServerPort);
    }

    /**
     * Same as {@link #SocksSocket(SocksProxy, String, int)}
     *
     * @param proxy   Socks proxy.
     * @param address Remote server's IP address.
     * @param port    Remote server's port.
     * @throws SocksException If any error about SOCKS protocol occurs.
     * @throws IOException    If I/O error occurs.
     */
    public SocksSocket(SocksProxy proxy, InetAddress address, int port) throws SocksException,
            IOException {
        this(proxy, new InetSocketAddress(address, port));
    }

    public SocksSocket(SocksProxy proxy, SocketAddress socketAddress) throws SocksException,
            IOException {
        checkNotNull(proxy, "Argument [proxy] may not be null");
        checkNotNull(socketAddress, "Argument [socketAddress] may not be null");
        checkArgument(socketAddress instanceof InetSocketAddress, "Unsupported address type");
        InetSocketAddress address = (InetSocketAddress) socketAddress;
        this.proxy = proxy.copy();
        this.remoteServerHost = address.getHostString();
        this.remoteServerPort = address.getPort();
        this.proxy.buildConnection();
        proxySocket = this.proxy.getProxySocket();
        initProxyChain();
        this.proxy.requestConnect(address.getAddress(), address.getPort());

    }

    /**
     * Creates an unconnected socket.
     *
     * @param proxy SOCKS proxy.
     * @throws IOException If an I/O error occurred.
     */
    public SocksSocket(SocksProxy proxy) throws IOException {
        this(proxy, proxy.createProxySocket());
    }

    /**
     * Creates a SocksSocket instance with a {@link SocksProxy} and a
     *
     * @param proxy       SOCKS proxy.
     * @param proxySocket a unconnected socket. it will connect SOCKS server later.
     */
    public SocksSocket(SocksProxy proxy, Socket proxySocket) {
        checkNotNull(proxy, "Argument [proxy] may not be null");
        checkNotNull(proxySocket, "Argument [proxySocket] may not be null");
        checkArgument(!proxySocket.isConnected(), "Proxy socket should be unconnected");
        this.proxySocket = proxySocket;
        this.proxy = proxy.copy();
        this.proxy.setProxySocket(proxySocket);
    }

    /**
     * Initialize proxy chain.
     *
     * @throws SocketException If a SOCKS protocol error occurred.
     * @throws IOException     If an I/O error occurred.
     */
    private void initProxyChain() throws SocketException, IOException {
        List<SocksProxy> proxyChain = new ArrayList<SocksProxy>();
        SocksProxy temp = proxy;
        while (temp.getChainProxy() != null) {
            temp.getChainProxy().setProxySocket(proxySocket);
            proxyChain.add(temp.getChainProxy());
            temp = temp.getChainProxy();
        }
        logger.debug("Proxy chain has:{} proxy", proxyChain.size());
        if (!proxyChain.isEmpty()) {
            SocksProxy pre = proxy;
            for (SocksProxy chain : proxyChain) {
                pre.requestConnect(chain.getInetAddress(), chain.getPort());
                proxy.getChainProxy().buildConnection();
                pre = chain;
            }
        }

    }

    /**
     * Connect to SOCKS Server and server will proxy remote server.
     *
     * @param host Remote server's host.
     * @param port Remote server's port.
     * @throws SocksException If any error about SOCKS protocol occurs.
     * @throws IOException    If I/O error occurs.
     */
    public void connect(String host, int port) throws SocksException, IOException {
        this.remoteServerHost = checkNotNull(host, "Argument [host] may not be null");
        this.remoteServerPort = checkNotNull(port, "Argument [port] may not be null");
        proxy.buildConnection();
        initProxyChain();
        proxy.requestConnect(remoteServerHost, remoteServerPort);
    }


    @Override
    public void connect(SocketAddress endpoint) throws SocksException, IOException {
        connect(endpoint, 0);
    }


    @Override
    public void connect(SocketAddress endpoint, int timeout) throws SocksException, IOException {

        if (!(endpoint instanceof InetSocketAddress)) {
            throw new IllegalArgumentException("Unsupported address type");
        }

        remoteServerHost = ((InetSocketAddress) endpoint).getHostName();
        remoteServerPort = ((InetSocketAddress) endpoint).getPort();

        proxy.getProxySocket().setSoTimeout(timeout);
        proxy.buildConnection();
        initProxyChain();
        proxy.requestConnect(endpoint);

    }

    @Override
    public InputStream getInputStream() throws IOException {
        return proxy.getProxySocket().getInputStream();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return proxy.getProxySocket().getOutputStream();
    }

    @Override
    public void bind(SocketAddress bindpoint) throws IOException {
        proxy.getProxySocket().bind(bindpoint);
    }

    @Override
    public InetAddress getInetAddress() {
        try {
            return InetAddress.getByName(remoteServerHost);
        } catch (UnknownHostException e) {
        }
        return null;
    }

    @Override
    public InetAddress getLocalAddress() {
        return proxy.getProxySocket().getLocalAddress();
    }

    @Override
    public int getPort() {
        return remoteServerPort;
    }

    @Override
    public int getLocalPort() {
        return proxy.getProxySocket().getLocalPort();
    }

    @Override
    public SocketAddress getRemoteSocketAddress() {
        return proxy.getProxySocket().getRemoteSocketAddress();
    }

    @Override
    public SocketAddress getLocalSocketAddress() {
        return proxy.getProxySocket().getLocalSocketAddress();
    }

    @Override
    public SocketChannel getChannel() {
        return proxy.getProxySocket().getChannel();
    }

    @Override
    public boolean getTcpNoDelay() throws SocketException {
        return proxy.getProxySocket().getTcpNoDelay();
    }

    @Override
    public void setTcpNoDelay(boolean on) throws SocketException {
        proxy.getProxySocket().setTcpNoDelay(on);
    }

    @Override
    public void setSoLinger(boolean on, int linger) throws SocketException {
        proxy.getProxySocket().setSoLinger(on, linger);
    }

    @Override
    public int getSoLinger() throws SocketException {
        return proxy.getProxySocket().getSoLinger();
    }

    @Override
    public void sendUrgentData(int data) throws IOException {
        proxy.getProxySocket().sendUrgentData(data);
    }

    @Override
    public boolean getOOBInline() throws SocketException {
        return proxy.getProxySocket().getOOBInline();
    }

    @Override
    public void setOOBInline(boolean on) throws SocketException {
        proxy.getProxySocket().setOOBInline(on);
    }

    @Override
    public synchronized int getSoTimeout() throws SocketException {
        return proxy.getProxySocket().getSoTimeout();
    }

    @Override
    public synchronized void setSoTimeout(int timeout) throws SocketException {
        proxy.getProxySocket().setSoTimeout(timeout);
    }

    @Override
    public synchronized int getSendBufferSize() throws SocketException {
        return proxy.getProxySocket().getSendBufferSize();
    }

    @Override
    public synchronized void setSendBufferSize(int size) throws SocketException {
        proxy.getProxySocket().setSendBufferSize(size);
    }

    @Override
    public synchronized int getReceiveBufferSize() throws SocketException {
        return proxy.getProxySocket().getReceiveBufferSize();
    }

    @Override
    public synchronized void setReceiveBufferSize(int size) throws SocketException {
        proxy.getProxySocket().setReceiveBufferSize(size);
    }

    @Override
    public boolean getKeepAlive() throws SocketException {
        return proxy.getProxySocket().getKeepAlive();
    }

    @Override
    public void setKeepAlive(boolean on) throws SocketException {
        proxy.getProxySocket().setKeepAlive(on);
    }

    @Override
    public int getTrafficClass() throws SocketException {
        return proxy.getProxySocket().getTrafficClass();
    }

    @Override
    public void setTrafficClass(int tc) throws SocketException {
        proxy.getProxySocket().setTrafficClass(tc);
    }

    @Override
    public boolean getReuseAddress() throws SocketException {
        return proxy.getProxySocket().getReuseAddress();
    }

    @Override
    public void setReuseAddress(boolean on) throws SocketException {
        proxy.getProxySocket().setReuseAddress(on);
    }

    @Override
    public synchronized void close() throws IOException {
        proxy.getProxySocket().close();
        proxy.setProxySocket(null);
    }

    @Override
    public void shutdownInput() throws IOException {
        proxy.getProxySocket().shutdownInput();
    }

    @Override
    public void shutdownOutput() throws IOException {
        proxy.getProxySocket().shutdownOutput();
    }

    @Override
    public boolean isConnected() {
        return proxy.getProxySocket().isConnected();
    }

    @Override
    public boolean isBound() {
        return proxy.getProxySocket().isBound();
    }

    @Override
    public boolean isClosed() {
        return proxy.getProxySocket().isClosed();
    }

    @Override
    public boolean isInputShutdown() {
        return proxy.getProxySocket().isInputShutdown();
    }

    @Override
    public boolean isOutputShutdown() {
        return proxy.getProxySocket().isOutputShutdown();
    }

    @Override
    public void setPerformancePreferences(int connectionTime, int latency, int bandwidth) {
        proxy.getProxySocket().setPerformancePreferences(connectionTime, latency, bandwidth);
    }

    public Socket getProxySocket() {
        return proxy.getProxySocket();
    }

}
