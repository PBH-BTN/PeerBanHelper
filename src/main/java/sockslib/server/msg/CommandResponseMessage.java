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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sockslib.common.AddressType;
import sockslib.common.NotImplementException;
import sockslib.utils.SocksUtil;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * The class <code>CommandResponseMessage</code> represents a command response message.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Apr 6, 2015 11:10:25 AM
 */
public class CommandResponseMessage implements WritableMessage {

    /**
     * Logger that subclasses also can use.
     */
    protected static final Logger logger = LoggerFactory.getLogger(CommandResponseMessage.class);

    private int version = 5;

    /**
     * The reserved field.
     */
    private int reserved = 0x00;

    /**
     * Address type.
     */
    private int addressType = AddressType.IPV4;

    /**
     * Bind address.
     */
    private InetAddress bindAddress;

    /**
     * Bind port.
     */
    private int bindPort;

    /**
     * Rely from SOCKS server.
     */
    private ServerReply reply;

    /**
     * Constructs a {@link CommandResponseMessage} by {@link ServerReply}.
     *
     * @param reply Reply from server.
     */
    public CommandResponseMessage(ServerReply reply) {
        byte[] defaultAddress = {0, 0, 0, 0};
        this.reply = reply;
        try {
            bindAddress = InetAddress.getByAddress(defaultAddress);
            addressType = 0x01;
        } catch (UnknownHostException e) {
            logger.error(e.getMessage(), e);
        }
    }

    /**
     * Constructs a {@link CommandResponseMessage}.
     *
     * @param version     Version
     * @param reply       Sever reply.
     * @param bindAddress Bind IP address.
     * @param bindPort    Bind port.
     */
    public CommandResponseMessage(int version, ServerReply reply, InetAddress bindAddress, int
            bindPort) {
        this.version = version;
        this.reply = reply;
        this.bindAddress = bindAddress;
        this.bindPort = bindPort;
        if (bindAddress.getAddress().length == 4) {
            addressType = 0x01;
        } else {
            addressType = 0x04;
        }
    }

    @Override
    public byte[] getBytes() {
        byte[] bytes = null;

        switch (addressType) {
            case AddressType.IPV4:
                bytes = new byte[10];
                for (int i = 0; i < bindAddress.getAddress().length; i++) {
                    bytes[i + 4] = bindAddress.getAddress()[i];
                }
                bytes[8] = SocksUtil.getFirstByteFromInt(bindPort);
                bytes[9] = SocksUtil.getSecondByteFromInt(bindPort);
                break;
            case AddressType.IPV6:
                bytes = new byte[22];
                for (int i = 0; i < bindAddress.getAddress().length; i++) {
                    bytes[i + 4] = bindAddress.getAddress()[i];
                }
                bytes[20] = SocksUtil.getFirstByteFromInt(bindPort);
                bytes[21] = SocksUtil.getSecondByteFromInt(bindPort);
                break;
            case AddressType.DOMAIN_NAME:
                throw new NotImplementException();
            default:
                break;
        }

        bytes[0] = (byte) version;
        bytes[1] = reply.getValue();
        bytes[2] = (byte) reserved;
        bytes[3] = (byte) addressType;

        return bytes;
    }

    @Override
    public int getLength() {
        return getBytes().length;
    }

    /**
     * Returns version.
     *
     * @return Version.
     */
    public int getVersion() {
        return version;
    }

    /**
     * Sets version.
     *
     * @param version Version.
     */
    public void setVersion(int version) {
        this.version = version;
    }

    /**
     * Returns address type.
     *
     * @return Address type.
     */
    public int getAddressType() {
        return addressType;
    }

    /**
     * Sets address type.
     *
     * @param addressType Address type.
     */
    public void setAddressType(int addressType) {
        this.addressType = addressType;
    }

    /**
     * Returns bind address.
     *
     * @return Bind address.
     */
    public InetAddress getBindAddress() {
        return bindAddress;
    }

    /**
     * Sets bind address.
     *
     * @param bindAddress Bind address.
     */
    public void setBindAddress(InetAddress bindAddress) {
        this.bindAddress = bindAddress;
    }

    /**
     * Returns bind port.
     *
     * @return Bind port.
     */
    public int getBindPort() {
        return bindPort;
    }

    /**
     * Sets bind port.
     *
     * @param bindPort Bind port.
     */
    public void setBindPort(int bindPort) {
        this.bindPort = bindPort;
    }

    /**
     * Returns the reply of SOCKS server.
     *
     * @return SOCKS server's reply.
     */
    public ServerReply getReply() {
        return reply;
    }

    /**
     * Sets SOCKS server's reply.
     *
     * @param reply Reply of the SOCKS server.
     */
    public void setReply(ServerReply reply) {
        this.reply = reply;
    }

}
