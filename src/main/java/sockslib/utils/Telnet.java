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

package sockslib.utils;

import java.io.IOException;
import java.net.SocketAddress;

/**
 * The interface <code>Telnet</code> represent a simple telnet tool which can send data to server
 * and then receive data from server.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Oct 19, 2015 2:20 PM
 * @see TCPTelnet
 * @see UDPTelnet
 */
public interface Telnet {

    /**
     * Send data to specified remote server and received data form server, finally
     * close connection.
     *
     * @param data Data which will be sent to remote server.
     * @param host Remote server's host.
     * @param port Remote server's port.
     * @return Data received from remote server.
     * @throws IOException If any I/O error occurred.
     */
    byte[] request(final byte[] data, final String host, final int port) throws IOException;

    /**
     * Send data to specified remote server and received data form server, finally
     * close connection.
     *
     * @param data    Data which will be sent to remote server.
     * @param address Remote server's address.
     * @return Data received from remote server.
     * @throws IOException If any I/O error occurred.
     */
    byte[] request(final byte[] data, final SocketAddress address) throws IOException;
}
