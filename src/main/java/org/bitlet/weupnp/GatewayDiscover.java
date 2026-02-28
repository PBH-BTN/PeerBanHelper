/*
 *              weupnp - Trivial upnp java library
 *
 * Copyright (C) 2008 Alessandro Bahgat Shehata, Daniele Castagna
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * Alessandro Bahgat Shehata - ale dot bahgat at gmail dot com
 * Daniele Castagna - daniele dot castagna at gmail dot com
 *
 */
package org.bitlet.weupnp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

/**
 * Handles the discovery of GatewayDevices, via the {@link org.bitlet.weupnp.GatewayDiscover#discover()} method.
 */
public class GatewayDiscover {

    /**
     * The SSDP port
     */
    public static final int PORT = 1900;

    /**
     * The broadcast address to use when trying to contact UPnP devices
     */
    public static final String IP = "239.255.255.250";

    /**
     * The default timeout for the initial broadcast request
     */
    private static final int DEFAULT_TIMEOUT = 3000;

    /**
     * The timeout for the initial broadcast request
     */
    private int timeout = DEFAULT_TIMEOUT;

    /**
     * The gateway types the discover have to search.
     */
    private String[] searchTypes;
    
    /**
     * The default gateway types to use in search
     */
    private static final String[] DEFAULT_SEARCH_TYPES =
        {
            "urn:schemas-upnp-org:device:InternetGatewayDevice:1",
            "urn:schemas-upnp-org:service:WANIPConnection:1",
            "urn:schemas-upnp-org:service:WANPPPConnection:1"
        };
            
    
    /**
     * A map of the GatewayDevices discovered so far.
     * The assumption is that a machine is connected to up to a Gateway Device
     * per InetAddress
     */
    private final Map<InetAddress, GatewayDevice> devices = new HashMap<InetAddress, GatewayDevice>();

    /*
      *  Thread class for sending a search datagram and process the response.
      */
    private class SendDiscoveryThread extends Thread {
        InetAddress ip;
        String searchMessage;

        SendDiscoveryThread(InetAddress localIP, String searchMessage) {
            this.ip = localIP;
            this.searchMessage = searchMessage;
        }

        @Override
        public void run() {

            DatagramSocket ssdp = null;

            try {
                // Create socket bound to specified local address
                ssdp = new DatagramSocket(new InetSocketAddress(ip, 0));

                byte[] searchMessageBytes = searchMessage.getBytes();
                DatagramPacket ssdpDiscoverPacket = new DatagramPacket(searchMessageBytes, searchMessageBytes.length);
                ssdpDiscoverPacket.setAddress(InetAddress.getByName(IP));
                ssdpDiscoverPacket.setPort(PORT);

                ssdp.send(ssdpDiscoverPacket);
                ssdp.setSoTimeout(GatewayDiscover.this.timeout);

                boolean waitingPacket = true;
                while (waitingPacket) {
                    DatagramPacket receivePacket = new DatagramPacket(new byte[1536], 1536);
                    try {
                        ssdp.receive(receivePacket);
                        byte[] receivedData = new byte[receivePacket.getLength()];
                        System.arraycopy(receivePacket.getData(), 0, receivedData, 0, receivePacket.getLength());

                        // Create GatewayDevice from response
                        GatewayDevice gatewayDevice = parseMSearchReply(receivedData);

                        gatewayDevice.setLocalAddress(ip);
                        gatewayDevice.loadDescription();

                        // verify that the search type is among the requested ones
                        if (Arrays.asList(searchTypes).contains(gatewayDevice.getSt())) {
                            synchronized (devices) {
                                devices.put(ip, gatewayDevice);
                                break; // device added for this ip, nothing further to do
                            }
                        }
                    } catch (SocketTimeoutException ste) {
                        waitingPacket = false;
                    } catch (Exception e) {
                        // Handles the case of weird devices that respond to the query but then do not give a
                        // valid xml url
                    }
                }

            } catch (Exception e) {
                // e.printStackTrace();
            } finally {
                if (null != ssdp) {
                    ssdp.close();
                }
            }
        }
    }

    /**
     * Constructor.
     * 
     * By default it's looking for 3 types of gateways.
     * 
     */
    public GatewayDiscover() {
        this(DEFAULT_SEARCH_TYPES);
    }

    /**
     * Constructor of the gateway discover service.
     * 
     * @param st The search type you are looking for
     */
    public GatewayDiscover(String st) {
        this(new String[]{st});
    }
    
    /**
     * Constructor.
     * 
     * @param types The search types the discover have to look for
     */
    public GatewayDiscover(String[] types) {
        this.searchTypes = types;
    }

    /**
     * Gets the timeout for socket connections of the initial broadcast request.
     * @return timeout in milliseconds
     */
    public int getTimeout() {
        return this.timeout;
    }

    /**
     * Sets the timeout for socket connections of the initial broadcast request.
     * @param milliseconds the new timeout in milliseconds
     */
    public void setTimeout(int milliseconds) {
        this.timeout = milliseconds;
    }

    /**
     * Discovers Gateway Devices on the network(s) the executing machine is
     * connected to.
     * <p/>
     * The host may be connected to different networks via different network
     * interfaces.
     * Assumes that each network interface has a different InetAddress and
     * returns a map associating every GatewayDevice (responding to a broadcast
     * discovery message) with the InetAddress it is connected to.
     *
     * @return a map containing a GatewayDevice per InetAddress
     * @throws SocketException
     * @throws UnknownHostException
     * @throws IOException
     * @throws SAXException
     * @throws ParserConfigurationException
     */
    public Map<InetAddress, GatewayDevice> discover() throws SocketException, UnknownHostException, IOException, SAXException, ParserConfigurationException {

        Collection<InetAddress> ips = getLocalInetAddresses(true, false, false);

        for (int i = 0; i < searchTypes.length; i++) {

            String searchMessage = "M-SEARCH * HTTP/1.1\r\n" +
                    "HOST: " + IP + ":" + PORT + "\r\n" +
                    "ST: " + searchTypes[i] + "\r\n" +
                    "MAN: \"ssdp:discover\"\r\n" +
                    "MX: 2\r\n" +    // seconds to delay response
                    "\r\n";

            // perform search requests for multiple network adapters concurrently
            Collection<SendDiscoveryThread> threads = new ArrayList<SendDiscoveryThread>();
            for (InetAddress ip : ips) {
                SendDiscoveryThread thread = new SendDiscoveryThread(ip, searchMessage);
                threads.add(thread);
                thread.start();
            }

            // wait for all search threads to finish
            for (SendDiscoveryThread thread : threads)
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    // continue with next thread
                }

            // If a search type found devices, don't try with different search type
            if (!devices.isEmpty())
                break;

        } // loop SEARCHTYPES

        return devices;
    }

    /**
     * Parses the reply from UPnP devices
     *
     * @param reply the raw bytes received as a reply
     * @return the representation of a GatewayDevice
     */
    private GatewayDevice parseMSearchReply(byte[] reply) {

        GatewayDevice device = new GatewayDevice();

        String replyString = new String(reply);
        StringTokenizer st = new StringTokenizer(replyString, "\n");

        while (st.hasMoreTokens()) {
            String line = st.nextToken().trim();

            if (line.isEmpty())
                continue;

            if (line.startsWith("HTTP/1.") || line.startsWith("NOTIFY *"))
                continue;

            String key = line.substring(0, line.indexOf(':'));
            String value = line.length() > key.length() + 1 ? line.substring(key.length() + 1) : null;

            key = key.trim();
            if (value != null) {
                value = value.trim();
            }

            if (key.compareToIgnoreCase("location") == 0) {
                device.setLocation(value);

            } else if (key.compareToIgnoreCase("st") == 0) {    // Search Target
                device.setSt(value);
            }
        }

        return device;
    }

    /**
     * Gets the first connected gateway
     *
     * @return the first GatewayDevice which is connected to the network, or
     *         null if none present
     */
    public GatewayDevice getValidGateway() {

        for (GatewayDevice device : devices.values()) {
            try {
                if (device.isConnected()) {
                    return device;
                }
            } catch (Exception e) {
            }
        }

        return null;
    }

    /**
     * Returns list of all discovered gateways. Is empty when no gateway is found.
     */
    public Map<InetAddress, GatewayDevice> getAllGateways() {
        return devices;
    }

    /**
     * Retrieves all local IP addresses from all present network devices.
     *
     * @param getIPv4            boolean flag if IPv4 addresses shall be retrieved
     * @param getIPv6            boolean flag if IPv6 addresses shall be retrieved
     * @param sortIPv4BeforeIPv6 if true, IPv4 addresses will be sorted before IPv6 addresses
     * @return Collection if {@link InetAddress}es
     */
    private List<InetAddress> getLocalInetAddresses(boolean getIPv4, boolean getIPv6, boolean sortIPv4BeforeIPv6) {
        List<InetAddress> arrayIPAddress = new ArrayList<InetAddress>();
        int lastIPv4Index = 0;

        // Get all network interfaces
        Enumeration<NetworkInterface> networkInterfaces;
        try {
            networkInterfaces = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            return arrayIPAddress;
        }

        if (networkInterfaces == null)
            return arrayIPAddress;

        // For every suitable network interface, get all IP addresses
        while (networkInterfaces.hasMoreElements()) {
            NetworkInterface card = networkInterfaces.nextElement();

            try {
                // skip devices, not suitable to search gateways for
                if (card.isLoopback() || card.isPointToPoint() ||
                        card.isVirtual() || !card.isUp())
                    continue;
            } catch (SocketException e) {
                continue;
            }

            Enumeration<InetAddress> addresses = card.getInetAddresses();

            if (addresses == null)
                continue;

            while (addresses.hasMoreElements()) {
                InetAddress inetAddress = addresses.nextElement();
                int index = arrayIPAddress.size();

                if (!getIPv4 || !getIPv6) {
                    if (getIPv4 && !Inet4Address.class.isInstance(inetAddress))
                        continue;

                    if (getIPv6 && !Inet6Address.class.isInstance(inetAddress))
                        continue;
                } else if (sortIPv4BeforeIPv6 && Inet4Address.class.isInstance(inetAddress)) {
                    index = lastIPv4Index++;
                }

                arrayIPAddress.add(index, inetAddress);
            }
        }

        return arrayIPAddress;
    }

}
