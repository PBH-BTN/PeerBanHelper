package com.ghostchu.peerbanhelper.wrapper;

import inet.ipaddr.AddressStringException;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class PeerAddress implements Comparable<PeerAddress> {

    private String ip;
    @Getter
    private IPAddress address;
    /**
     * 端口可能为 0 （代表未设置）
     */
    private int port;

    public PeerAddress(String ip, int port) {
        this.ip = ip;
        this.port = port;
        try {
            this.address = new IPAddressString(ip).toAddress();

            if(this.address.isIPv4Convertible()){
                IPAddress ipv4Convert = this.address.toIPv4();
                if(ipv4Convert != null){
                    this.address = this.address.toIPv4();
                }
            }
        } catch (AddressStringException e) {
            log.warn("Failed to parse Address to IPAddress, please report to developer! IP="+ip, e);
        }
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
