/*
 * Copyright 2015-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package sockslib.common;

import com.google.common.base.Preconditions;

import java.io.Serializable;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The class <code>IPRange</code> represents an IPrange.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date May 2, 2015 12:45:25 AM
 */
public class IPRange implements Iterable<IP>, Serializable {

    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Starting IP address of the IP address range.
     */
    private final IP startIP;

    /**
     * End IP address of the IP address range。
     */
    private final IP endIP;

    /**
     * Constructs a <code>IpRange</code> instance by given tow IP.
     *
     * @param startIp IP starts.
     * @param endIp   IP ends.
     */
    public IPRange(IP startIp, IP endIp) {

        int result = endIp.compareTo(startIp);
        if (result > 0 || result == 0)
            Preconditions.checkArgument(
                    result > 0 || result == 0, "maxIP must equal or bigger than minIP");

        this.startIP = startIp;
        this.endIP = endIp;
    }

    /**
     * Creates a <code>IpRange</code> instance by a string.
     *
     * @param range a string such as "1.1.1.1-1.1.2.255".
     * @return IP range.
     */
    public static IPRange parse(String range) {
        String[] ips = range.split("-");
        Preconditions.checkArgument(
                ips.length == 2,
                "IP range string must be fomarted as [minIP-maxIP],error argument:" + range);
        return new IPRange(IP.parseFromString(ips[0]), IP.parseFromString(ips[1]));
    }


    /**
     * Creates a {@link IPRange} instance by IP with mask.
     *
     * @param ipWithMask IP/mask, such as 192.168.70.1/24
     * @return {@link IPRange} instance
     */
    public static IPRange parseFromIPWithMask(String ipWithMask) {
        long minIpAsLong = 0;
        long maxIpAsLong = 0;
        String[] strs = ipWithMask.split("/");

        if (strs.length == 2) {
            IP ip = IP.parseFromString(strs[0]);
            int mask = Integer.parseInt(strs[1]);
            long maskAsLong = 0xffffffff << (32 - mask);
            minIpAsLong = ip.toLong();
            maxIpAsLong = minIpAsLong | (~maskAsLong);
        } else {
            throw new IllegalArgumentException(
                    "The input String format error. For example" + " 192.168.1.1/24");
        }
        return new IPRange(new IP(minIpAsLong), new IP(maxIpAsLong));
    }


    /**
     * Gets A class IP range.
     *
     * @return A class IP range.
     */
    public static IPRange AClassLocalIPRange() {
        // 10.0.0.0 - 10.255.255.255
        return new IPRange(new IP(0x0A000000), new IP(0x0AFFFFFF));
    }

    /**
     * Gets B class IP range.
     *
     * @return B class IP range.
     */
    public static IPRange BClassLocalIPRange() {
        return new IPRange(new IP(172, 16, 0, 0), new IP(172, 31, 255, 255));
    }

    /**
     * Gets C class IP range.
     *
     * @return C class IP range.
     */
    public static IPRange CClassLocalIPRange() {
        return new IPRange(new IP(192, 168, 0, 0), new IP(192, 168, 255, 255));
    }


    /**
     * Returns <code>true</code> if the given IP is in the IP range.
     *
     * @param ip IP.
     * @return If the IP is in the rang return <code>true</code>.
     */
    public boolean contains(IP ip) {
        return ip.compareTo(startIP) >= 0 && ip.compareTo(endIP) <= 0;
    }

    public boolean contains(SocketAddress address) {
        return address instanceof InetSocketAddress && contains(
                ((InetSocketAddress) address).getAddress());
    }

    public boolean contains(InetAddress address) {
        if (address instanceof Inet4Address) {
            IP ip = new IP(address.getAddress());
            return contains(ip);
        }
        return false;
    }

    /**
     * Returns size of IP range.
     *
     * @return Size of IP range.
     */
    public long size() {
        return (endIP.getValue() - startIP.getValue() + 1L);
    }

    /**
     * Returns starting IP address.
     *
     * @return Starting IP address.
     */
    public IP getStartIP() {
        return startIP;
    }

    /**
     * Returns end IP address.
     *
     * @return End ip address.
     */
    public IP getEndIP() {
        return endIP;
    }

    /**
     * Split IP address range by a IP address.
     *
     * @param ip IP address. IP address range should contains the IP address.
     * @return List of IP address ranges.
     */
    public List<IPRange> split(IP ip) {
        List<IPRange> ranges = new ArrayList<IPRange>();
        if (this.contains(ip)) {
            ranges.add(new IPRange(this.startIP, ip));
            ranges.add(new IPRange(ip, this.endIP));
        }
        return ranges;
    }

    @Override
    public Iterator<IP> iterator() {
        return new IPIterator(startIP, endIP);
    }

    @Override
    public String toString() {
        return startIP + "-" + endIP;
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof IPRange) {
            IPRange range = (IPRange) obj;
            return range.getStartIP().equals(startIP) && range.getEndIP().equals(endIP);
        } else {
            return false;
        }
    }

}
