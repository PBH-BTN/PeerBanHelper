package com.ghostchu.peerbanhelper.util.traversal.stun;

import com.ghostchu.peerbanhelper.text.Lang;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public class TcpStunClient {
    private final int sourcePort;

    public static class ServerUnavailable extends IOException {
        public ServerUnavailable(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final List<String> stunServerList;
    private final String sourceHost;
    private int currentServerIndex = 0;

    public TcpStunClient(List<String> stunServerList, String sourceHost, int localPort) {
        if (stunServerList == null || stunServerList.isEmpty()) {
            throw new IllegalArgumentException("STUN server list cannot be empty");
        }
        this.stunServerList = stunServerList;
        this.sourceHost = sourceHost;
        this.sourcePort = localPort;
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
                    log.error(tlUI(Lang.STUN_CLIENT_NO_AVAILABLE_SERVER), stunServerList, e);
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
        return getMappingTcp(stunHost, stunPort);
    }

    private MappingResult getMappingTcp(String stunHost, int stunPort) throws IOException {
        try (Socket socket = StunSocketTool.getSocket()) {
            //socket.setSoLinger(true, 0);
            socket.setReuseAddress(true);
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
}
