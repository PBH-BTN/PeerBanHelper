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

/**
 * The interface <code>DatagramPacketDecapsulation</code> represents a datagram packet
 * decapsulation.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Mar 24, 2015 9:07:34 PM
 */
public interface DatagramPacketDecapsulation {

    /**
     * Decapsulates a datagram packet.
     *
     * @param packet Datagram packet that need to be decapsulated.
     * @throws SocksException If any error about SOCKS protocol occurs.
     */
    void decapsulate(DatagramPacket packet) throws SocksException;

}
