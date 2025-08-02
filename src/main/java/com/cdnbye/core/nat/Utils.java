package com.cdnbye.core.nat;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class Utils {
    public static byte[] ipToBytes(String ip) throws UnknownHostException {
        return InetAddress.getByName(ip).getAddress();
    }

}
