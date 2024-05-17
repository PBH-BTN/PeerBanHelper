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

/**
 * The enumeration <code>SocksCommand</code> represents SOCKS command.<br>
 * SOCKS4 protocol support CONNECT and BIND, SOCKS5 protocol supports CONNECT, BIND, and UDP
 * ASSOCIATE.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Mar 19, 2015 11:41:34 AM
 * @see <a href="http://www.ietf.org/rfc/rfc1928.txt">SOCKS Protocol Version 5</a>
 */
public enum SocksCommand {

    /**
     * Supported by SOCKS4 and SOCKS5 protocol.
     */
    CONNECT(0x01),
    /**
     * Supported by SOCKS4 and SOCKS5 protocol.
     */
    BIND(0x02),

    /**
     * Only supported by SOCKS5 protocol.
     */
    UDP_ASSOCIATE(0x03);

    /**
     * the unsigned byte that represents the command.
     */
    private int value;

    /**
     * Constructs a SOCKS command.
     *
     * @param value Value of SOCKS command.
     */
    private SocksCommand(int value) {
        this.value = value;
    }

    /**
     * Get value of a command.
     *
     * @return the unsigned byte that represents the command.
     */
    public int getValue() {
        return value;
    }
}
