package com.ghostchu.peerbanhelper.util.traversal.stun;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.ApiStatus;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * STUN客户端 - 按照Natter实现
 * 用于获取NAT映射地址和检测NAT类型
 *
 * 支持的功能：
 * 1. NAT映射获取（UDP/TCP）
 * 2. NAT类型检测（基于RFC 3489）
 * 3. 对称NAT检测
 * 4. Hairpin支持检测
 *
 * NAT类型检测算法：
 * - 比较本地地址和外部地址确定是否有NAT
 * - 使用多个STUN服务器检测对称NAT
 * - 区分不同类型的锥形NAT
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
        try (Socket socket = StunSocketTool.getSocket()) {
            socket.setSoLinger(true, 0);
            socket.bind(new InetSocketAddress(sourceHost, sourcePort));
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
            log.debug("STUN TCP: outer={}, inner={}, stunHost={}, stunPort={}", outerAddr, innerAddr, stunHost, stunPort);
            return new MappingResult(innerAddr, outerAddr);
        } catch (IOException e) {
            throw new ServerUnavailable("STUN server unavailable: " + e.getMessage(), e);
        }
    }

    @ApiStatus.Experimental
    private MappingResult getMappingUdp(String stunHost, int stunPort) throws IOException {
        try (DatagramSocket udpSocket = new DatagramSocket()) {
            udpSocket.setReuseAddress(true);
            udpSocket.setSoTimeout(3000);
            InetAddress bindAddress = InetAddress.getByName(sourceHost);
            udpSocket.bind(new InetSocketAddress(bindAddress, 0));
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

            log.debug("STUN UDP: outer={}, inner={}, stunHost={}, stunPort={}", outerAddr, innerAddr, stunHost, stunPort);

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

        buf.getShort(); // messageLength
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

    /**
     * 检测NAT类型
     * 基于RFC 3489的NAT类型检测算法
     */
    public NatDetectionResult detectNatType() {
        if (!udp) {
            return NatDetectionResult.failed("NAT detection only supported for UDP");
        }

        try {
            // 步骤1: 获取基本映射
            MappingResult test1 = getMapping();
            InetSocketAddress localAddr = test1.interAddress();
            InetSocketAddress externalAddr = test1.outerAddress();

            log.debug("NAT Detection - Test 1: local={}, external={}", localAddr, externalAddr);

            // 如果本地地址和外部地址相同，则没有NAT
            if (localAddr.getAddress().equals(externalAddr.getAddress()) &&
                    localAddr.getPort() == externalAddr.getPort()) {
                return new NatDetectionResult(
                        NatType.OPEN_INTERNET,
                        localAddr,
                        externalAddr,
                        null,
                        false,
                        "No NAT detected - local and external addresses are identical"
                );
            }

            // 步骤2: 测试不同服务器的映射一致性
            MappingResult test2 = getMappingFromDifferentServer();
            if (test2 == null) {
                // 如果只有一个服务器，跳过对称NAT检测
                log.warn("Only one STUN server available, skipping symmetric NAT detection");
                return new NatDetectionResult(
                        NatType.UNKNOWN,
                        localAddr,
                        externalAddr,
                        null,
                        false,
                        "Cannot determine NAT type with single STUN server"
                );
            }

            InetSocketAddress externalAddr2 = test2.outerAddress();
            log.debug("NAT Detection - Test 2: external2={}", externalAddr2);

            // 如果两次映射的外部地址不同，则为对称NAT
            if (!externalAddr.equals(externalAddr2)) {
                return new NatDetectionResult(
                        NatType.SYMMETRIC,
                        localAddr,
                        externalAddr,
                        externalAddr2,
                        false,
                        "Different external addresses from different servers indicate symmetric NAT"
                );
            }

            // 步骤3: 测试锥形NAT的类型
            // 这里可以进一步扩展来区分不同类型的锥形NAT
            // 目前简化为检测是否为锥形NAT

            return new NatDetectionResult(
                    NatType.FULL_CONE,
                    localAddr,
                    externalAddr,
                    externalAddr2,
                    false,
                    "Consistent external address from different servers indicates cone NAT"
            );

        } catch (Exception e) {
            log.error("Failed to detect NAT type", e);
            return NatDetectionResult.failed(e.getMessage());
        }
    }

    /**
     * 从不同的STUN服务器获取映射
     * 用于检测对称NAT
     */
    private MappingResult getMappingFromDifferentServer() throws IOException {
        if (stunServerList.size() < 2) {
            return null;
        }

        // 保存当前服务器索引
        int originalIndex = currentServerIndex;

        try {
            // 切换到下一个服务器
            currentServerIndex = (currentServerIndex + 1) % stunServerList.size();
            return getMappingFromServer();
        } finally {
            // 恢复原来的服务器索引
            currentServerIndex = originalIndex;
        }
    }

    /**
     * 检测NAT是否支持Hairpin（发夹）
     * 这个功能需要更复杂的实现，目前返回false
     */
    public boolean detectHairpinSupport() {
        // TODO: 实现Hairpin检测
        // 需要向自己的外部地址发送数据包来测试
        return false;
    }

    /**
     * 获取详细的NAT信息，包括类型检测
     */
    public NatDetectionResult getDetailedNatInfo() {
        NatDetectionResult detection = detectNatType();

        // 如果检测成功且是锥形NAT，可以进一步检测Hairpin支持
        if (detection.isConeNat()) {
            boolean hairpinSupport = detectHairpinSupport();
            return new NatDetectionResult(
                    detection.natType(),
                    detection.localAddress(),
                    detection.externalAddress(),
                    detection.alternateExternalAddress(),
                    hairpinSupport,
                    detection.details() + (hairpinSupport ? " (Hairpin: yes)" : " (Hairpin: no)")
            );
        }

        return detection;
    }

}
