package com.cdnbye.core.nat;

import java.net.InetSocketAddress;

public class StunResult {

    // Gets public IP end point. This value is null if failed to get network type.
    public InetSocketAddress getIpAddr() {
        return ipAddr;
    }

    // Gets UDP network type.
    public NatType getNatType() {
        return natType;
    }

    private final InetSocketAddress ipAddr;
    private final NatType natType;

    public StunResult(NatType natType, InetSocketAddress ipAddr) {
        this.natType = natType;
        this.ipAddr = ipAddr;
    }


}
