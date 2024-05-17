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

import sockslib.common.SocksException;
import sockslib.common.methods.SocksMethod;

import java.io.IOException;
import java.net.Socket;
import java.util.List;

/**
 * The interface <code>SocksMethodRequester</code> is a tool that can send request message from
 * SOCKS server and get a method that server accepted.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Mar 19, 2015 10:43:41 AM
 * @see GenericSocksMethodRequester
 */
public interface SocksMethodRequester {

    /**
     * Send request message to server.<br>
     * <p>
     * This method will send list of methods to SOCKS server and receive the method that SOCKS server
     * selected.
     * </p>
     *
     * @param acceptableMethods Methods that client can handle.
     * @param socket            The socket instance that has connected SOCKS server.
     * @param socksVersion      SOCKS protocol version
     * @return Method that server accepted.
     * @throws SocksException If any errors about SOCKS protocol occurred.
     * @throws IOException    if any IO errors occurred.
     */
    public SocksMethod doRequest(List<SocksMethod> acceptableMethods, Socket socket, int
            socksVersion) throws SocksException, IOException;
}
