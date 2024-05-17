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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sockslib.common.AddressType;
import sockslib.utils.SocksUtil;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;

/**
 * The class <code>RequestCmdReplyMessage</code> represents the message that
 * sent by SOCKS server when client sends a command request.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Mar 23, 2015 5:55:06 PM
 */
public class CommandReplyMessage implements SocksMessage {

    /**
     * Logger that subclasses also can use.
     */
    protected Logger logger = LoggerFactory.getLogger(CommandReplyMessage.class);

    /**
     * The bytes that received from SOCKS server.
     */
    private byte[] replyBytes;

    /**
     * Constructs an instance of {@link CommandReplyMessage} with an array of
     * bytes that received from SOCKS server.
     *
     * @param replyBytes The bytes that received from SOCKS server.
     */
    public CommandReplyMessage(byte[] replyBytes) {
        this.replyBytes = replyBytes;
    }

    /**
     * Returns <code>true</code> if the command request is success.
     *
     * @return If the command request is success, it will return
     * <code>true</code>.
     */
    public boolean isSuccess() {
        if (replyBytes.length < 10) {
            return false;
        }
        return replyBytes[1] == 0;
    }

    /**
     * Gets IP address from the bytes that sent by SOCKS server.
     *
     * @return IP address.
     * @throws UnknownHostException If the host is unknown.
     */
    public InetAddress getIp() throws UnknownHostException {
        byte[] addressBytes = null;

        if (replyBytes[3] == AddressType.IPV4) {
            addressBytes = new byte[4];
        } else if (replyBytes[3] == AddressType.IPV6) {
            addressBytes = new byte[16];
        }

        System.arraycopy(replyBytes, 4, addressBytes, 0, addressBytes.length);
        return InetAddress.getByAddress(addressBytes);
    }

    /**
     * Gets port from bytes that sent by SOCKS server.
     *
     * @return port.
     */
    public int getPort() {

        return SocksUtil.bytesToInt(replyBytes[replyBytes.length - 2], replyBytes[replyBytes.length
                - 1]);
    }

    /**
     * Returns the bytes that sent by SOCKS server.
     *
     * @return The bytes that sent by SOCKS server.
     */
    public byte[] getReplyBytes() {
        return replyBytes;
    }

    /**
     * Sets reply bytes.
     *
     * @param replyBytes The bytes that sent by SOCKS server.
     */
    public void setReplyBytes(byte[] replyBytes) {
        this.replyBytes = replyBytes;
    }

    /**
     * Gets the socket address.
     *
     * @return Socket address.
     */
    public SocketAddress getSocketAddress() {
        try {
            return new InetSocketAddress(getIp(), getPort());
        } catch (UnknownHostException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

}
