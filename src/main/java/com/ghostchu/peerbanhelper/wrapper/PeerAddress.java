package com.ghostchu.peerbanhelper.wrapper;

import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import inet.ipaddr.IPAddress;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Slf4j
public class PeerAddress implements Comparable<PeerAddress> {

    private String ip;
    private transient IPAddress address;
    /**
     * 端口可能为 0 （代表未设置）
     */
    private int port;

    public PeerAddress(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    public IPAddress getAddress() {
        if (address == null) { // 可能由 Gson 反序列化时导致此值为空
            address = IPAddressUtil.getIPAddress(ip);
        }
        return address;
    }


    @Override
    public int compareTo(PeerAddress o) {
        int r = ip.compareTo(o.ip);
        if (r == 0) {
            return Integer.compare(port, o.port);
        }
        return r;
    }

}
