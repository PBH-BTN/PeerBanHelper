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

package sockslib.client;

import sockslib.common.SocksCommand;
import sockslib.common.SocksException;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;

/**
 * The interface <code>SocksCommandSender</code> can send SOCKS command to SOCKS server.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Mar 19, 2015 11:39:43 AM
 * @see SocksCommand
 * @see <a href="http://www.ietf.org/rfc/rfc1928.txt">SOCKS Protocol Version 5</a>
 */
public interface SocksCommandSender {

    static final int RESERVED = 0x00;
    static final byte ATYPE_IPV4 = 0x01;
    static final byte ATYPE_DOMAINNAME = 0x03;
    static final byte ATYPE_IPV6 = 0x04;
    static final int REP_SUCCEEDED = 0x00;
    static final int REP_GENERAL_SOCKS_SERVER_FAILURE = 0x01;
    static final int REP_CONNECTION_NOT_ALLOWED_BY_RULESET = 0x02;
    static final int REP_NETWORK_UNREACHABLE = 0x03;
    static final int REP_HOST_UNREACHABLE = 0x04;
    static final int REP_CONNECTION_REFUSED = 0x05;
    static final int REP_TTL_EXPIRED = 0x06;
    static final int REP_COMMAND_NOT_SUPPORTED = 0x07;
    static final int REP_ADDRESS_TYPE_NOT_SUPPORTED = 0x08;

    /**
     * Send a command to SOCKS server.
     *
     * @param socket  Socket that has connected SOCKS server.
     * @param command The Command such as CONNECT, BIND, UDP ASSOCIATE.
     * @param address Remote server IPv4 or IPv6 address.
     * @param port    Remote server port.
     * @param version The version of SOCKS protocol.
     * @return The bytes received from SOCKS server.
     * @throws SocksException If any error about SOCKS protocol occurs.
     * @throws IOException    If any I/O error occurs.
     */
    public CommandReplyMessage send(Socket socket, SocksCommand command, InetAddress address, int
            port, int version) throws SocksException, IOException;

    /**
     * Send a command to SOCKS server.
     *
     * @param socket  Socket that has connected SOCKS server.
     * @param command The Command such as CONNECT, BIND, UDP ASSOCIATE.
     * @param address Remote server address.
     * @param version The version of SOCKS protocol.
     * @return The bytes received from SOCKS server.
     * @throws SocksException If any error about SOCKS protocol occurs.
     * @throws IOException    If any I/O error occurs.
     */
    public CommandReplyMessage send(Socket socket, SocksCommand command, SocketAddress address, int
            version) throws SocksException, IOException;

    /**
     * Send a command to SOCKS server and resolve domain name in SOCKS server.
     *
     * @param socket  Socket that has connected SOCKS server.
     * @param command The Command such as CONNECT, BIND, UDP ASSOCIATE.
     * @param host    Remote server host. The host will be resolved in SOCKS server.
     * @param port    Remote server port.
     * @param version The version of SOCKS protocol.
     * @return The bytes received from SOCKS server.
     * @throws SocksException If any error about SOCKS protocol occurs.
     * @throws IOException    If any I/O error occurs.
     */
    public CommandReplyMessage send(Socket socket, SocksCommand command, String host, int port, int
            version) throws SocksException, IOException;

    public CommandReplyMessage checkServerReply(InputStream inputStream) throws SocksException,
            IOException;
}
