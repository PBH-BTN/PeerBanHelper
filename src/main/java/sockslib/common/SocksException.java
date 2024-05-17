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

import sockslib.server.msg.ServerReply;

import java.io.IOException;

/**
 * The class <code>SocksException</code> represents an exception about SOCKS protocol.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Mar 18, 2015 9:24:59 AM
 */
public class SocksException extends IOException {

    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 1L;

    private static final String NO_ACCEPTABLE_METHODS = "NO ACCEPTABLE METHODS";
    /**
     * Messages that server will reply.
     */
    private static final String serverReplyMessage[] =
            {"General SOCKS server failure", "Connection not allowed by ruleset",
                    "Network " + "unreachable", "Host unreachable", "Connection refused", "TTL expired",
                    "Command not " + "supported", "Address type not supported"};
    /**
     * Reply from SOCKS server.
     */
    private ServerReply serverReply;

    /**
     * Constructs an instance of {@link SocksException} with a message.
     *
     * @param msg Message.
     */
    public SocksException(String msg) {
        super(msg);
    }

    /**
     * Constructs an instance of {@link SocksException} with a code.
     *
     * @param replyCode The code that server Replied.
     */
    public SocksException(int replyCode) {

    }

    /**
     * Returns a {@link SocksException} instance with a message "NO ACCEPTABLE METHODS".
     *
     * @return An instance of {@link SocksException}.
     */
    public static SocksException noAcceptableMethods() {
        return new SocksException(NO_ACCEPTABLE_METHODS);
    }

    /**
     * Returns a {@link SocksException} instance with a message "Protocol not supported".
     *
     * @return An instance of {@link SocksException}.
     */
    public static SocksException protocolNotSupported() {
        return new SocksException("Protocol not supported");
    }

    /**
     * Returns a {@link SocksException} instance with a message of reply.
     *
     * @param reply Server's reply.
     * @return An instance of {@link SocksException}.
     */
    public static SocksException serverReplyException(ServerReply reply) {
        SocksException ex = serverReplyException(reply.getValue());
        ex.setServerReply(reply);
        return ex;
    }

    /**
     * Returns a {@link SocksException} instance with a message of reply.
     *
     * @param reply Code of server's reply.
     * @return An instance of {@link SocksException}.
     */
    public static SocksException serverReplyException(byte reply) {
        int code = reply;
        code = code & 0xff;
        if (code < 0 || code > 0x08) {
            return new SocksException("Unknown reply");
        }
        code = code - 1;
        return new SocksException(serverReplyMessage[code]);
    }

    /**
     * Returns server's reply.
     *
     * @return Server's reply.
     */
    public ServerReply getServerReply() {
        return serverReply;
    }

    /**
     * Sets server's reply.
     *
     * @param serverReply Reply of the server.
     */
    public void setServerReply(ServerReply serverReply) {
        this.serverReply = serverReply;
    }

}
