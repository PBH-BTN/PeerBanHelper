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

import com.google.common.hash.Hashing;
import sockslib.utils.UnsignedByte;

import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;


/**
 * The class <code>IP</code> represents an IP v4 address.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date May 2, 2015 12:50:28 AM
 */
public class IP implements Comparable<IP>, Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Value of IP as long.
     */
    private final long value;

    /**
     * First number of the IP address.
     */
    private final int a;

    /**
     * Second number of the IP address.
     */
    private final int b;

    /**
     * Third number of the IP address.
     */
    private final int c;

    /**
     * Last number of the IP address.
     */
    private final int d;


    /**
     * Constructs IP by four numbers.
     *
     * @param a First number of the IP address.
     * @param b Second number of the IP address.
     * @param c Third number of the IP address.
     * @param d Last number of the IP address.
     */
    public IP(int a, int b, int c, int d) {

        checkArgument(checkRange(a) && checkRange(b) && checkRange(c)
                && checkRange(d), "Each number of IP must in 0 ~ 255");
        this.a = a;
        this.b = b;
        this.c = c;
        this.d = d;
        value = this.toLong();
    }

    /**
     * Constructs IP by a long integer.
     *
     * @param ip IP as Long integer.
     */
    public IP(long ip) {
        checkArgument(ip <= 0xffffffffL && ip >= 0, "Invalid IP");
        value = ip;
        a = (int) (ip >>> 24);
        b = (int) ((ip & 0x00ffffff) >>> 16);
        c = (int) ((ip & 0x0000ffff) >>> 8);
        d = (int) (ip & 0x000000ff);
    }

    /**
     * Constructs IP by bytes.
     *
     * @param address Bytes of address.
     */
    public IP(byte[] address) {
        a = UnsignedByte.toInt(address[0]);
        b = UnsignedByte.toInt(address[1]);
        c = UnsignedByte.toInt(address[2]);
        d = UnsignedByte.toInt(address[3]);
        value = this.toLong();
    }

    /**
     * Creates a IP instance by a string.
     *
     * @param ip IP as a string. such as "192.168.1.1".
     * @return Instance of <code>Ip</code>.
     */
    public static IP parseFromString(String ip) {
        String regex = "\\s*(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\s*";

        Pattern pattern = Pattern.compile(regex);
        Matcher m = pattern.matcher(ip);

        checkArgument(m.find(), "IP string should match the regex:%s", regex);

        int a = Integer.parseInt(m.group(1));
        int b = Integer.parseInt(m.group(2));
        int c = Integer.parseInt(m.group(3));
        int d = Integer.parseInt(m.group(4));

        return new IP(a, b, c, d);
    }

    /**
     * Gets max IP which represents <b>255.255.255.255]</b>.
     *
     * @return Max IP address.
     */
    public static IP MAX_IP() {
        return new IP(0xffffffffL);
    }

    /**
     * Gets minimum IP which represents <b>0.0.0.0</b>.
     *
     * @return Minimum IP address.
     */
    public static IP MIN_IP() {
        return new IP(0L);
    }

    public static boolean isValid(String ip) {
        try {
            IP.parseFromString(ip);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public int getA() {
        return a;
    }

    public int getB() {
        return b;
    }

    public int getC() {
        return c;
    }

    public int getD() {
        return d;
    }

    /**
     * Gets next IP address. If the IP address doesn't have next IP then return <code>null</code>.
     *
     * @return Next IP address.
     */
    public IP nextIP() {
        return new IP(value + 1);
    }

    /**
     * Gets previous IP address. If the IP address doesn't have previous IP then return
     * <code>null</code>.
     *
     * @return Previous IP address.
     */
    public IP preIP() {
        return new IP(value - 1);
    }

    /**
     * Returns <code>true</code> if the IP is local IP address.
     *
     * @return <code>true</code> if the IP is local IP address.
     */
    public boolean isLocalIP() {

        return IPRange.AClassLocalIPRange().contains(this)
                || IPRange.BClassLocalIPRange().contains(this)
                || IPRange.CClassLocalIPRange().contains(this);
    }

    /**
     * Returns <code>true</code> if the IP can be used in Internet.
     *
     * @return <code>true</code> if the IP can be used in Internet.
     */
    public boolean isUseInInternet() {
        return !isLocalIP();
    }

    /**
     * Returns IP as a long integer.
     *
     * @return IP as a long integer.
     */
    public long toLong() {
        long a = this.a;
        long b = this.b;
        long c = this.c;
        long d = this.d;
        return ((a << 24) | (b << 16) | (c << 8) | d);
    }

    @Override
    public int compareTo(IP ip) {
        return (value > ip.getValue() ? 1 : value < ip.getValue() ? -1 : 0);
    }


    public long getValue() {
        return value;
    }

    @Override
    public String toString() {
        return String.format("%s.%s.%s.%s", a, b, c, d);
    }


    @Override
    public boolean equals(Object obj) {
        if (obj instanceof IP) {
            IP ip = (IP) obj;
            if (getValue() == ip.getValue()) {
                return true;
            }
        }
        return false;
    }


    @Override
    public int hashCode() {
        return Hashing.md5().newHasher().putInt(a).putChar('.').putInt(b).putChar('.').putInt(c)
                .putChar('.').putInt(d).hash().hashCode();
    }


    /**
     * Returns <code>true</code> if the number is in 0~255.
     *
     * @param num a number.
     * @return <code>true</code> if the number is in 0~255.
     */
    private boolean checkRange(int num) {
        return num >= 0 && num <= 255;
    }


}
