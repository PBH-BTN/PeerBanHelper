package com.ghostchu.peerbanhelper.wrapper;

import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.google.common.net.HostAndPort;
import inet.ipaddr.IPAddress;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

import java.io.Serializable;
import java.util.Objects;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Slf4j
public final class PeerAddress implements Comparable<PeerAddress>, Serializable {

    private String downloaderRawIp;
    private Integer downloaderRawPort;

    private String teredoClientIp;
    private int teredoClientUdpPort;

    private String nattedClientIp;
    private int nattedClientPort;


    private String ip;
    private transient IPAddress address;
    /**
     * 端口可能为 0 （代表未设置）
     */
    private int port;

    private boolean natTranslated;
    private boolean teredoTranslated;

    public PeerAddress(String ip, int port, String rawIp) {
        this.ip = ip;
        this.downloaderRawIp = rawIp;
        this.downloaderRawPort = port;
        this.port = port;
    }

    public IPAddress getAddress() {
        if (address == null) {
            address = IPAddressUtil.getIPAddress(ip).toPrefixBlock();
        }
        return address;
    }

    public PeerAddress setNat(String nattedIp, int nattedPort) {
        this.ip = nattedIp;
        this.port = nattedPort;
        this.nattedClientIp = nattedIp;
        this.nattedClientPort = nattedPort;
        this.natTranslated = true;
        this.address = null; // clear cached address
        return this;
    }

    public PeerAddress setTeredo(String teredoIp, int teredoPort) {
        this.ip = teredoIp;
        this.port = teredoPort;
        this.teredoClientIp = teredoIp;
        this.teredoClientUdpPort = teredoPort;
        this.teredoTranslated = true;
        this.address = null; // clear cached address;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PeerAddress that)) return false;
        return port == that.port && Objects.equals(downloaderRawIp, that.downloaderRawIp) && Objects.equals(downloaderRawPort, that.downloaderRawPort) && Objects.equals(ip, that.ip) && Objects.equals(address, that.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(downloaderRawIp, downloaderRawPort, ip, address, port);
    }

    @Override
    public int compareTo(@NonNull PeerAddress o) {
        // downloaderRaw, then ip, then port
        int cmp = downloaderRawIp.compareTo(o.downloaderRawIp);
        if (cmp != 0) return cmp;
        cmp = Integer.compare(downloaderRawPort, o.downloaderRawPort);
        if (cmp != 0) return cmp;
        cmp = ip.compareTo(o.ip);
        if (cmp != 0) return cmp;
        return Integer.compare(port, o.port);
    }
}
