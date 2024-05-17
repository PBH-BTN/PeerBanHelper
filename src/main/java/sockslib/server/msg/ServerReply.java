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

package sockslib.server.msg;

/**
 * The enumeration <code>ServerReply</code> represents reply of servers will SOCKS client send a
 * command request to the SOCKS server.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date May 4, 2015 4:01:14 AM
 */
public enum ServerReply {

    /**
     * Succeeded.
     */
    SUCCEEDED(0x00),

    /**
     * General SOCKS server failure.
     */
    GENERAL_SOCKS_SERVER_FAILURE(0x01),

    /**
     * Connection not allowed by ruleset.
     */
    CONNECTION_NOT_ALLOWED_BY_RULESET(0x02),

    /**
     * Network unreachable.
     */
    NETWORK_UNREACHABLE(0x03),

    /**
     * Host unreachable.
     */
    HOST_UNREACHABLE(0x04),

    /**
     * Connection refused.
     */
    CONNECTION_REFUSED(0x05),

    /**
     * TTL expired.
     */
    TTL_EXPIRED(0x06),

    /**
     * Command not supported.
     */
    COMMAND_NOT_SUPPORTED(0x07),

    /**
     * Address type not supported.
     */
    ADDRESS_TYPE_NOT_SUPPORTED(0x08);

    /**
     * Code of the reply.
     */
    private byte value;

    /**
     * A private constructor.
     *
     * @param value Reply code.
     */
    private ServerReply(int value) {
        this.value = (byte) value;
    }

    /**
     * Returns reply code in byte.
     *
     * @return Reply code in byte.
     */
    public byte getValue() {
        return value;
    }

}
