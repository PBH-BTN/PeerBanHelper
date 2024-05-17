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
import sockslib.common.SocksException;
import sockslib.common.methods.SocksMethod;
import sockslib.common.methods.SocksMethodRegistry;
import sockslib.utils.LogMessageBuilder;
import sockslib.utils.LogMessageBuilder.MsgType;
import sockslib.utils.StreamUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.List;

/**
 * The class <code>GenericSocksMethodRequester</code> implements {@link SocksMethodRequester}.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Mar 19, 2015 10:48:42 AM
 * @see <a href="http://www.ietf.org/rfc/rfc1928.txt">SOCKS Protocol Version 5</a>
 */
public class GenericSocksMethodRequester implements SocksMethodRequester {

    /**
     * Logger that subclasses also can use.
     */
    protected static final Logger logger = LoggerFactory.getLogger(GenericSocksMethodRequester.class);

    @Override
    public SocksMethod doRequest(List<SocksMethod> acceptableMethods, Socket socket, int
            socksVersion) throws SocksException, IOException {
        InputStream inputStream = socket.getInputStream();
        OutputStream outputStream = socket.getOutputStream();
        byte[] bufferSent = new byte[2 + acceptableMethods.size()];

        bufferSent[0] = (byte) socksVersion;
        bufferSent[1] = (byte) acceptableMethods.size();
        for (int i = 0; i < acceptableMethods.size(); i++) {
            bufferSent[2 + i] = (byte) acceptableMethods.get(i).getByte();
        }

        outputStream.write(bufferSent);
        outputStream.flush();

        logger.debug("{}", LogMessageBuilder.build(bufferSent, MsgType.SEND));

        // Received data.
        byte[] receivedData = StreamUtil.read(inputStream, 2);
        logger.debug("{}", LogMessageBuilder.build(receivedData, MsgType.RECEIVE));

        if (receivedData[0] != socksVersion) {
            throw new SocksException("Remote server don't support SOCKS5");
        }

        return SocksMethodRegistry.getByByte(receivedData[1]);
    }

}
