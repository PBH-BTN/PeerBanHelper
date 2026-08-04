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
    private String downloaderRawPort;

    private String teredoClientIp;
    private int teredoClientUdpPort;


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
        this.downloaderRawPort = ip;
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
        this.natTranslated = true;
        this.address = null; // clear cached address
        return this;
    }

    public PeerAddress setTeredo(String teredoIp, int teredoPort) {
        this.ip = teredoIp;
        this.port = teredoPort;
        this.teredoTranslated = true;
        this.address = null; // clear cached address;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PeerAddress that)) return false;
        return teredoClientUdpPort == that.teredoClientUdpPort && port == that.port && natTranslated == that.natTranslated && teredoTranslated == that.teredoTranslated && Objects.equals(downloaderRawIp, that.downloaderRawIp) && Objects.equals(downloaderRawPort, that.downloaderRawPort) && Objects.equals(teredoClientIp, that.teredoClientIp) && Objects.equals(ip, that.ip);
    }

    @Override
    public int hashCode() {
        return Objects.hash(downloaderRawIp, downloaderRawPort, teredoClientIp, teredoClientUdpPort, ip, port, natTranslated, teredoTranslated);
    }

    @Override
    public int compareTo(@NonNull PeerAddress o) {
        int ipCompare = this.getAddress().compareTo(o.getAddress());
        if (ipCompare != 0) return ipCompare;
        return Integer.compare(this.port, o.port);
    }
}
