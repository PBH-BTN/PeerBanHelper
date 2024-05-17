package sockslib.common.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The class <code>MonitorSocketWrapper</code> is wrapper of {@link Socket}.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Sep 21, 2015 11:40 AM
 */
public class MonitorSocketWrapper extends Socket {

    private Socket originalSocket;
    private List<SocketMonitor> monitors;
    private InputStream inputStream = null;
    private OutputStream outputStream = null;

    public MonitorSocketWrapper(Socket socket, SocketMonitor... monitors) {
        this.originalSocket = checkNotNull(socket, "Argument [socket] may not be null");
        this.monitors = new ArrayList<>(monitors.length);
        Collections.addAll(this.monitors, monitors);
    }

    public MonitorSocketWrapper(Socket socket, List<SocketMonitor> monitors) {
        this.originalSocket = checkNotNull(socket, "Argument [socket] may not be null");
        this.monitors = checkNotNull(monitors, "Arugment [monitors] may not be null");
    }

    public MonitorSocketWrapper(Socket socket) {
        this.originalSocket = checkNotNull(socket, "Argument [socket] may not be null");
    }

    public static Socket wrap(Socket socket, SocketMonitor... monitors) {
        return new MonitorSocketWrapper(socket, monitors);
    }

    public static Socket wrap(Socket socket, List<SocketMonitor> monitors) {
        return new MonitorSocketWrapper(socket, monitors);
    }

    public MonitorSocketWrapper addMonitor(SocketMonitor monitor) {
        if (monitors == null) {
            monitors = new ArrayList<>(1);
        }
        monitors.add(monitor);
        return this;
    }

    public MonitorSocketWrapper removeMonitor(SocketMonitor monitor) {
        if (monitors != null) {
            monitors.remove(monitor);
        }
        return this;
    }


    public Socket getOriginalSocket() {
        return originalSocket;
    }

    public void setOriginalSocket(Socket originalSocket) {
        this.originalSocket = originalSocket;
    }

    public List<SocketMonitor> getMonitors() {
        return monitors;
    }

    public void setMonitors(List<SocketMonitor> monitors) {
        this.monitors = monitors;
    }

    @Override
    public void connect(SocketAddress endpoint) throws IOException {
        originalSocket.connect(endpoint);
    }

    @Override
    public void connect(SocketAddress endpoint, int timeout) throws IOException {
        originalSocket.connect(endpoint, timeout);
    }

    @Override
    public void bind(SocketAddress bindpoint) throws IOException {
        originalSocket.bind(bindpoint);
    }

    @Override
    public InetAddress getInetAddress() {
        return originalSocket.getInetAddress();
    }

    @Override
    public InetAddress getLocalAddress() {
        return originalSocket.getLocalAddress();
    }

    @Override
    public int getPort() {
        return originalSocket.getPort();
    }

    @Override
    public int getLocalPort() {
        return originalSocket.getLocalPort();
    }

    @Override
    public SocketAddress getRemoteSocketAddress() {
        return originalSocket.getRemoteSocketAddress();
    }

    @Override
    public SocketAddress getLocalSocketAddress() {
        return originalSocket.getLocalSocketAddress();
    }

    @Override
    public SocketChannel getChannel() {
        return originalSocket.getChannel();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        if (inputStream == null) {
            inputStream = getInputStreamFromSocket();
        }
        return inputStream;
    }

    public InputStream getInputStreamFromSocket() throws IOException {
        List<InputStreamMonitor> inputStreamMonitors = new ArrayList<>(monitors.size());
        if (monitors != null) {
            for (SocketMonitor socketMonitor : monitors) {
                inputStreamMonitors.add(socketMonitor);
            }
        }
        return MonitorInputStreamWrapper.wrap(originalSocket.getInputStream(), inputStreamMonitors);
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        if (outputStream == null) {
            outputStream = getOutputStreamFromSocket();
        }
        return outputStream;
    }

    public OutputStream getOutputStreamFromSocket() throws IOException {
        List<OutputStreamMonitor> outputStreamMonitors = new ArrayList<>(monitors.size());
        if (monitors != null) {
            for (SocketMonitor socketMonitor : monitors) {
                outputStreamMonitors.add(socketMonitor);
            }
        }
        return MonitorOutputStreamWrapper.wrap(originalSocket.getOutputStream(), outputStreamMonitors);
    }

    @Override
    public boolean getTcpNoDelay() throws SocketException {
        return originalSocket.getTcpNoDelay();
    }

    @Override
    public void setTcpNoDelay(boolean on) throws SocketException {
        originalSocket.setTcpNoDelay(on);
    }

    @Override
    public void setSoLinger(boolean on, int linger) throws SocketException {
        originalSocket.setSoLinger(on, linger);
    }

    @Override
    public int getSoLinger() throws SocketException {
        return originalSocket.getSoLinger();
    }

    @Override
    public void sendUrgentData(int data) throws IOException {
        originalSocket.sendUrgentData(data);
    }

    @Override
    public boolean getOOBInline() throws SocketException {
        return originalSocket.getOOBInline();
    }

    @Override
    public void setOOBInline(boolean on) throws SocketException {
        originalSocket.setOOBInline(on);
    }

    @Override
    public synchronized int getSoTimeout() throws SocketException {
        return originalSocket.getSoTimeout();
    }

    @Override
    public synchronized void setSoTimeout(int timeout) throws SocketException {
        originalSocket.setSoTimeout(timeout);
    }

    @Override
    public synchronized int getSendBufferSize() throws SocketException {
        return originalSocket.getSendBufferSize();
    }

    @Override
    public synchronized void setSendBufferSize(int size) throws SocketException {
        originalSocket.setSendBufferSize(size);
    }

    @Override
    public synchronized int getReceiveBufferSize() throws SocketException {
        return originalSocket.getReceiveBufferSize();
    }

    @Override
    public synchronized void setReceiveBufferSize(int size) throws SocketException {
        originalSocket.setReceiveBufferSize(size);
    }

    @Override
    public boolean getKeepAlive() throws SocketException {
        return originalSocket.getKeepAlive();
    }

    @Override
    public void setKeepAlive(boolean on) throws SocketException {
        originalSocket.setKeepAlive(on);
    }

    @Override
    public int getTrafficClass() throws SocketException {
        return originalSocket.getTrafficClass();
    }

    @Override
    public void setTrafficClass(int tc) throws SocketException {
        originalSocket.setTrafficClass(tc);
    }

    @Override
    public boolean getReuseAddress() throws SocketException {
        return originalSocket.getReuseAddress();
    }

    @Override
    public void setReuseAddress(boolean on) throws SocketException {
        originalSocket.setReuseAddress(on);
    }

    @Override
    public synchronized void close() throws IOException {
        originalSocket.close();
    }

    @Override
    public void shutdownInput() throws IOException {
        originalSocket.shutdownInput();
    }

    @Override
    public void shutdownOutput() throws IOException {
        originalSocket.shutdownOutput();
    }

    @Override
    public String toString() {
        return originalSocket.toString();
    }

    @Override
    public boolean isConnected() {
        return originalSocket.isConnected();
    }

    @Override
    public boolean isBound() {
        return originalSocket.isBound();
    }

    @Override
    public boolean isClosed() {
        return originalSocket.isClosed();
    }

    @Override
    public boolean isInputShutdown() {
        return originalSocket.isInputShutdown();
    }

    @Override
    public boolean isOutputShutdown() {
        return originalSocket.isOutputShutdown();
    }

    @Override
    public void setPerformancePreferences(int connectionTime, int latency, int bandwidth) {
        originalSocket.setPerformancePreferences(connectionTime, latency, bandwidth);
    }

    @Override
    public int hashCode() {
        return originalSocket.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return originalSocket.equals(obj);
    }

}
