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

import java.net.DatagramPacket;
import java.net.SocketAddress;

/**
 * The interface <code>DatagramPacketEncapsulation</code> represents a datagram packet
 * encapsulation.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Mar 24, 2015 9:05:23 PM
 */
public interface DatagramPacketEncapsulation {

    /**
     * Encapsulates a datagram packet.
     *
     * @param packet      Datagram packet that need to be encapsulated.
     * @param destination Destination address.
     * @return Datagram packet that has encapsulated.
     * @throws SocksException If any error about SOCKS protocol occurs.
     */
    public DatagramPacket encapsulate(DatagramPacket packet, SocketAddress destination) throws
            SocksException;

}
