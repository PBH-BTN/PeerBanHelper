package com.ghostchu.peerbanhelper.wrapper;

import lombok.Data;

@Data
public class PeerAddress implements Comparable<PeerAddress> {

    private String ip;
    /**
     * 端口可能为 0 （代表未设置）
     */
    private int port;

    public PeerAddress(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    public PeerAddress() {
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
