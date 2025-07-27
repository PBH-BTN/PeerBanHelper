package com.ghostchu.peerbanhelper.util.traversal;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * STUN客户端 - 按照Natter实现
 * 用于获取NAT映射地址
 */
@Slf4j
public class StunClient {

    private final int sourcePort;

    public static class ServerUnavailable extends IOException {
        public ServerUnavailable(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    private final List<String> stunServerList;
    private final String sourceHost;
    private final boolean udp;
    private int currentServerIndex = 0;
    
    // 保持socket引用以便复用
    private DatagramSocket udpSocket;
    private Socket tcpSocket;

    public StunClient(List<String> stunServerList, String sourceHost, int localPort, boolean udp) {
        if (stunServerList == null || stunServerList.isEmpty()) {
            throw new IllegalArgumentException("STUN server list cannot be empty");
        }
        this.stunServerList = stunServerList;
        this.sourceHost = sourceHost;
        this.sourcePort = localPort;
        this.udp = udp;
    }
    
    /**
     * 获取NAT映射
     */
    public MappingResult getMapping() throws IOException {
        String firstServer = stunServerList.getFirst();
        while (true) {
            try {
                return getMappingFromServer();
            } catch (ServerUnavailable e) {
                // 轮换到下一个服务器
                currentServerIndex = (currentServerIndex + 1) % stunServerList.size();
                // 如果回到第一个服务器，说明所有服务器都试过了
                if (stunServerList.get(currentServerIndex).equals(firstServer)) {
                    log.error("No available STUN servers after trying all: {}", stunServerList);
                    try {
                        Thread.sleep(10000); // 等待10秒后重试
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }
    
    private String getCurrentServer() {
        return stunServerList.get(currentServerIndex);
    }
    
    private MappingResult getMappingFromServer() throws IOException {
        String serverStr = getCurrentServer();
        String[] parts = serverStr.split(":");
        String stunHost = parts[0];
        int stunPort = parts.length > 1 ? Integer.parseInt(parts[1]) : 3478;
        
        if (udp) {
            return getMappingUdp(stunHost, stunPort);
        } else {
            return getMappingTcp(stunHost, stunPort);
        }
    }
    
    private MappingResult getMappingTcp(String stunHost, int stunPort) throws IOException {
        try (Socket socket = new Socket()) {
            socket.setReuseAddress(true);
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(3000);
            
            // 按照Natter的做法，不绑定具体端口，让系统自动分配
            // 这样可以避免"Already bound"错误
            InetAddress bindAddress = InetAddress.getByName(sourceHost);
            socket.bind(new InetSocketAddress(bindAddress, sourcePort));
            
            // 连接到STUN服务器
            socket.connect(new InetSocketAddress(stunHost, stunPort));
            
            InetSocketAddress innerAddr = (InetSocketAddress) socket.getLocalSocketAddress();
            
            // 构造STUN Binding Request
            byte[] request = createStunBindingRequest();
            socket.getOutputStream().write(request);
            socket.getOutputStream().flush();
            
            // 读取响应
            byte[] buffer = new byte[1500];
            int bytesRead = socket.getInputStream().read(buffer);
            
            if (bytesRead < 20) {
                throw new ServerUnavailable("Invalid STUN response length", null);
            }
            
            // 解析响应
            InetSocketAddress outerAddr = parseStunResponse(buffer, bytesRead);

            log.debug("STUN TCP: outer={}, inner={}, stunHost={}, stunPort={}", outerAddr, innerAddr,  stunHost, stunPort);
            
            return new MappingResult(innerAddr, outerAddr);
            
        } catch (IOException e) {
            throw new ServerUnavailable("STUN server unavailable: " + e.getMessage(), e);
        }
    }
    
    private MappingResult getMappingUdp(String stunHost, int stunPort) throws IOException {
        try {
            // 如果socket不存在或已关闭，创建新的socket
            if (udpSocket == null || udpSocket.isClosed()) {
                // 按照Natter的做法创建UDP socket
                udpSocket = new DatagramSocket(null); // 不绑定到任何地址
                udpSocket.setReuseAddress(true);
                udpSocket.setSoTimeout(3000);
                
                // 强制使用IPv4，绑定到指定IP的任意端口
                InetAddress bindAddress = InetAddress.getByName(sourceHost);
                udpSocket.bind(new InetSocketAddress(bindAddress, 0));
            }
            
            InetSocketAddress innerAddr = (InetSocketAddress) udpSocket.getLocalSocketAddress();
            
            // 构造STUN Binding Request
            byte[] request = createStunBindingRequest();
            DatagramPacket requestPacket = new DatagramPacket(
                request, request.length, new InetSocketAddress(stunHost, stunPort));
            
            udpSocket.send(requestPacket);
            
            // 读取响应
            byte[] buffer = new byte[1500];
            DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);
            udpSocket.receive(responsePacket);
            
            // 解析响应
            InetSocketAddress outerAddr = parseStunResponse(buffer, responsePacket.getLength());
            
            log.debug("STUN UDP: outer={}, inner={}, stunHost={}, stunPort={}", outerAddr, innerAddr,  stunHost, stunPort);
            
            return new MappingResult(innerAddr, outerAddr);
            
        } catch (IOException e) {
            throw new ServerUnavailable("STUN server unavailable: " + e.getMessage(), e);
        }
    }
    
    /**
     * 创建STUN Binding Request
     * 基于RFC 5389
     */
    private byte[] createStunBindingRequest() {
        ByteBuffer buffer = ByteBuffer.allocate(20);
        
        // STUN Header
        buffer.putShort((short) 0x0001);  // Binding Request
        buffer.putShort((short) 0x0000);  // Length (no attributes)
        buffer.putInt(0x2112A442);        // Magic Cookie
        
        // Transaction ID (96 bits)
        buffer.putInt(0x4e415452);        // "NATR"
        buffer.putInt(ThreadLocalRandom.current().nextInt());
        buffer.putInt(ThreadLocalRandom.current().nextInt());
        
        return buffer.array();
    }
    
    /**
     * 解析STUN响应
     */
    private InetSocketAddress parseStunResponse(byte[] buffer, int length) throws IOException {
        if (length < 20) {
            throw new IOException("Invalid STUN response length");
        }
        
        ByteBuffer buf = ByteBuffer.wrap(buffer, 0, length);
        
        // 检查STUN头
        short messageType = buf.getShort();
        if (messageType != 0x0101) { // Binding Response
            throw new IOException("Invalid STUN response type: " + Integer.toHexString(messageType));
        }
        
        buf.getShort(); // messageLength (跳过)
        int magicCookie = buf.getInt();
        if (magicCookie != 0x2112A442) {
            throw new IOException("Invalid magic cookie");
        }
        
        // 跳过Transaction ID
        buf.position(20);
        
        // 解析属性
        String ip = null;
        int port = 0;
        
        while (buf.remaining() >= 4) {
            short attrType = buf.getShort();
            short attrLength = buf.getShort();
            
            if (attrType == 1 || attrType == 32) { // MAPPED-ADDRESS or XOR-MAPPED-ADDRESS
                if (attrLength >= 8) {
                    buf.get(); // Reserved
                    byte family = buf.get();
                    
                    if (family == 1) { // IPv4
                        short portValue = buf.getShort();
                        int ipValue = buf.getInt();
                        
                        if (attrType == 32) { // XOR-MAPPED-ADDRESS
                            portValue ^= 0x2112;
                            ipValue ^= 0x2112A442;
                        }
                        
                        port = portValue & 0xFFFF;
                        ip = String.format("%d.%d.%d.%d",
                            (ipValue >>> 24) & 0xFF,
                            (ipValue >>> 16) & 0xFF,
                            (ipValue >>> 8) & 0xFF,
                            ipValue & 0xFF);
                        
                        break;
                    }
                } else {
                    buf.position(buf.position() + attrLength);
                }
            } else {
                // 跳过其他属性
                buf.position(buf.position() + attrLength);
                
                // 属性按4字节对齐
                int padding = (4 - (attrLength % 4)) % 4;
                buf.position(buf.position() + padding);
            }
        }
        
        if (ip == null || port == 0) {
            throw new IOException("No mapped address found in STUN response");
        }
        
        return new InetSocketAddress(ip, port);
    }

    public DatagramSocket getUdpSocket() {
        return udpSocket;
    }

    public Socket getTcpSocket() {
        return tcpSocket;
    }

    /**
     * 关闭STUN客户端
     */
    public void close() {
        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
        }
        if (tcpSocket != null && !tcpSocket.isClosed()) {
            try {
                tcpSocket.close();
            } catch (IOException e) {
                log.debug("Unable to close TCP socket: {}", e.getMessage());
            }
        }
    }
}
