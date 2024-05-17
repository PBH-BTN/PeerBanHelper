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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sockslib.client.Socks5DatagramSocket;
import sockslib.server.Socks5Handler;
import sockslib.utils.SocksUtil;

import java.net.*;
import java.util.Arrays;

/**
 * The class <code>Socks5DatagramPacketHandler</code> represents a datagram packet handler.
 * <p>
 * This class can encapsulate a datagram packet or decapsulate a datagram packet to help
 * {@link Socks5DatagramSocket} and {@link Socks5Handler} to implement UDP ASSOCIATE.
 * </p>
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Mar 24, 2015 9:09:39 PM
 */
public class Socks5DatagramPacketHandler implements DatagramPacketEncapsulation,
        DatagramPacketDecapsulation {

    /**
     * Logger that subclasses also can use.
     */
    protected static final Logger logger = LoggerFactory.getLogger(Socks5DatagramPacketHandler.class);

    public Socks5DatagramPacketHandler() {

    }

    @Override
    public DatagramPacket encapsulate(DatagramPacket packet, SocketAddress destination) throws
            SocksException {
        if (destination instanceof InetSocketAddress) {
            InetSocketAddress destinationAddress = (InetSocketAddress) destination;
            final byte[] data = packet.getData();
            final InetAddress remoteServerAddress = packet.getAddress();
            final byte[] addressBytes = remoteServerAddress.getAddress();
            final int ADDRESS_LENGTH = remoteServerAddress.getAddress().length;
            final int remoteServerPort = packet.getPort();
            byte[] buffer = new byte[6 + packet.getLength() + ADDRESS_LENGTH];

            buffer[0] = buffer[1] = 0; // reserved byte
            buffer[2] = 0; // fragment byte
            buffer[3] = (byte) (ADDRESS_LENGTH == 4 ? AddressType.IPV4 : AddressType.IPV6);
            System.arraycopy(addressBytes, 0, buffer, 4, ADDRESS_LENGTH);
            buffer[4 + ADDRESS_LENGTH] = SocksUtil.getFirstByteFromInt(remoteServerPort);
            buffer[5 + ADDRESS_LENGTH] = SocksUtil.getSecondByteFromInt(remoteServerPort);
            System.arraycopy(data, 0, buffer, 6 + ADDRESS_LENGTH, packet.getLength());
            return new DatagramPacket(buffer, buffer.length, destinationAddress.getAddress(),
                    destinationAddress.getPort());
        } else {
            throw new IllegalArgumentException("Only support java.net.InetSocketAddress");
        }
    }

    @Override
    public void decapsulate(DatagramPacket packet) throws SocksException {
        final byte[] data = packet.getData();

        if (!(data[0] == 0 && data[1] == data[0])) {
            // check reserved byte.
            throw new SocksException("SOCKS version error");
        }
        if (data[2] != 0) {
            throw new SocksException("SOCKS fregment is not supported");
        }
        InetAddress remoteServerAddress = null;
        int remoteServerPort = -1;
        byte[] originalData = null;

        switch (data[3]) {

            case AddressType.IPV4:
                try {
                    remoteServerAddress = InetAddress.getByAddress(Arrays.copyOfRange(data, 4, 8));
                } catch (UnknownHostException e) {
                    logger.error(e.getMessage(), e);
                }
                remoteServerPort = SocksUtil.bytesToInt(data[8], data[9]);
                originalData = Arrays.copyOfRange(data, 10, packet.getLength());
                break;

            case AddressType.IPV6:
                try {
                    remoteServerAddress = InetAddress.getByAddress(Arrays.copyOfRange(data, 4, 20));
                } catch (UnknownHostException e) {
                    throw new SocksException("Unknown host");
                }
                remoteServerPort = SocksUtil.bytesToInt(data[20], data[21]);
                originalData = Arrays.copyOfRange(data, 22, packet.getLength());
                break;

            case AddressType.DOMAIN_NAME:
                final int DOMAIN_LENGTH = data[4];
                String domainName = new String(data, 5, DOMAIN_LENGTH);
                try {
                    remoteServerAddress = InetAddress.getByName(domainName);
                } catch (UnknownHostException e) {
                    logger.error(e.getMessage(), e);
                }
                remoteServerPort = SocksUtil.bytesToInt(data[5 + DOMAIN_LENGTH], data[6 + DOMAIN_LENGTH]);
                originalData = Arrays.copyOfRange(data, 7 + DOMAIN_LENGTH, packet.getLength());
                break;

            default:
                break;
        }

        packet.setAddress(remoteServerAddress);
        packet.setPort(remoteServerPort);
        packet.setData(originalData);
    }
}
