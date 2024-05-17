package sockslib.utils;

import sockslib.common.IP;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * @author Youchao Feng
 * @version 1.0
 * @date Nov 24, 2015 2:21 PM
 */
public class AddressUtil {

    public static IP toIP(SocketAddress socketAddress) {
        if (socketAddress instanceof InetSocketAddress) {
            byte[] address = ((InetSocketAddress) socketAddress).getAddress().getAddress();
            return new IP(address);
        } else {
            throw new IllegalArgumentException("Not support type:" + socketAddress.getClass().getName());
        }
    }

    public static boolean addressIn(SocketAddress address, String ip) {
        return false;
    }

    public static boolean addressIn(InetAddress address, String ip) {
        return false;
    }
}
