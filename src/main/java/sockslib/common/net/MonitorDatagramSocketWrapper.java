package sockslib.common.net;

import java.io.IOException;
import java.net.*;
import java.nio.channels.DatagramChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Youchao Feng
 * @version 1.0
 * @date Sep 23, 2015 11:31 AM
 */
public class MonitorDatagramSocketWrapper extends DatagramSocket {

    private List<DatagramSocketMonitor> monitors;

    private DatagramSocket originalDatagramSocket;

    public MonitorDatagramSocketWrapper() throws SocketException {

    }

    public MonitorDatagramSocketWrapper(DatagramSocket datagramSocket) throws SocketException {
        this.originalDatagramSocket = datagramSocket;
    }


    public MonitorDatagramSocketWrapper(DatagramSocket datagramSocket, DatagramSocketMonitor...
            monitors) throws SocketException {
        this.originalDatagramSocket = datagramSocket;
        this.monitors = new ArrayList<>(monitors.length);
        Collections.addAll(this.monitors, monitors);
    }

    public static void main(String[] args) throws SocketException {
        new MonitorDatagramSocketWrapper();
    }

    public DatagramSocket getOriginalDatagramSocket() {
        return originalDatagramSocket;
    }

    public void setOriginalDatagramSocket(DatagramSocket originalDatagramSocket) {
        this.originalDatagramSocket = originalDatagramSocket;
    }

    public List<DatagramSocketMonitor> getMonitors() {
        return monitors;
    }

    public void setMonitors(List<DatagramSocketMonitor> monitors) {
        this.monitors = monitors;
    }

    @Override
    public void send(DatagramPacket p) throws IOException {
        originalDatagramSocket.send(p);
        if (monitors != null) {
            for (DatagramSocketMonitor monitor : monitors) {
                monitor.onSend(p);
            }
        }
    }

    @Override
    public synchronized void receive(DatagramPacket p) throws IOException {
        originalDatagramSocket.receive(p);
        if (monitors != null) {
            for (DatagramSocketMonitor monitor : monitors) {
                monitor.onReceive(p);
            }
        }
    }

    @Override
    public synchronized void bind(SocketAddress addr) throws SocketException {
        //This class is only a wrapper, this method will do nothing.
    }

    @Override
    public void connect(InetAddress address, int port) {
        originalDatagramSocket.connect(address, port);
    }

    @Override
    public boolean isBound() {
        return originalDatagramSocket.isBound();
    }

    @Override
    public void connect(SocketAddress addr) throws SocketException {
        originalDatagramSocket.connect(addr);
    }

    @Override
    public void disconnect() {
        originalDatagramSocket.disconnect();
    }

    @Override
    public boolean isConnected() {
        return originalDatagramSocket.isConnected();
    }

    @Override
    public InetAddress getInetAddress() {
        return originalDatagramSocket.getInetAddress();
    }

    @Override
    public int getPort() {
        return originalDatagramSocket.getPort();
    }

    @Override
    public SocketAddress getLocalSocketAddress() {
        return originalDatagramSocket.getLocalSocketAddress();
    }

    @Override
    public SocketAddress getRemoteSocketAddress() {
        return originalDatagramSocket.getRemoteSocketAddress();
    }

    @Override
    public InetAddress getLocalAddress() {
        return originalDatagramSocket.getLocalAddress();
    }

    @Override
    public int getLocalPort() {
        return originalDatagramSocket.getLocalPort();
    }

    @Override
    public synchronized int getSoTimeout() throws SocketException {
        return originalDatagramSocket.getSoTimeout();
    }

    @Override
    public synchronized void setSoTimeout(int timeout) throws SocketException {
        originalDatagramSocket.setSoTimeout(timeout);
    }

    @Override
    public synchronized int getSendBufferSize() throws SocketException {
        return originalDatagramSocket.getSendBufferSize();
    }

    @Override
    public synchronized void setSendBufferSize(int size) throws SocketException {
        originalDatagramSocket.setSendBufferSize(size);
    }

    @Override
    public synchronized int getReceiveBufferSize() throws SocketException {
        return originalDatagramSocket.getReceiveBufferSize();
    }

    @Override
    public synchronized void setReceiveBufferSize(int size) throws SocketException {
        originalDatagramSocket.setReceiveBufferSize(size);
    }

    @Override
    public synchronized boolean getReuseAddress() throws SocketException {
        return originalDatagramSocket.getReuseAddress();
    }

    @Override
    public synchronized void setReuseAddress(boolean on) throws SocketException {
        originalDatagramSocket.setReuseAddress(on);
    }

    @Override
    public synchronized boolean getBroadcast() throws SocketException {
        return originalDatagramSocket.getBroadcast();
    }

    @Override
    public synchronized void setBroadcast(boolean on) throws SocketException {
        originalDatagramSocket.setBroadcast(on);
    }

    @Override
    public synchronized int getTrafficClass() throws SocketException {
        return originalDatagramSocket.getTrafficClass();
    }

    @Override
    public synchronized void setTrafficClass(int tc) throws SocketException {
        originalDatagramSocket.setTrafficClass(tc);
    }

    @Override
    public void close() {
        originalDatagramSocket.close();
    }

    @Override
    public boolean isClosed() {
        return originalDatagramSocket.isClosed();
    }

    @Override
    public DatagramChannel getChannel() {
        return originalDatagramSocket.getChannel();
    }
}
