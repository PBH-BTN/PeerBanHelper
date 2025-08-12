package com.cdnbye.core.nat;

import com.google.common.net.InetAddresses;

public class Utils {
    public static byte[] ipToBytes(String ip) {
        return InetAddresses.forString(ip).getAddress();
    }

}
