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

package sockslib.server;

import sockslib.client.SocksProxy;
import sockslib.common.SocksException;
import sockslib.server.msg.CommandMessage;

import java.io.IOException;

/**
 * The interface <code>SocksHandler</code> represents a socket handler.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Mar 25, 2015 11:33:28 AM
 */
public interface SocksHandler extends Runnable {

    /**
     * Handles a session.
     *
     * @param session Session.
     * @throws Exception If any error occurred.
     */
    void handle(Session session) throws Exception;

    /**
     * Do CONNECTE command.
     *
     * @param session        Session
     * @param commandMessage {@link CommandMessage} read from SOCKS client.
     * @throws SocksException If a SOCKS protocol error occurred.
     * @throws IOException    If a I/O error occurred.
     */
    void doConnect(Session session, CommandMessage commandMessage) throws SocksException, IOException;

    /**
     * Do BIND command.
     *
     * @param session        Session.
     * @param commandMessage {@link CommandMessage} read from SOCKS client.
     * @throws SocksException If a SOCKS protocol error occurred.
     * @throws IOException    If a I/O error occurred.
     */
    void doBind(Session session, CommandMessage commandMessage) throws SocksException, IOException;

    /**
     * Do UDP ASSOCIATE command.
     *
     * @param session        Session.
     * @param commandMessage {@link CommandMessage} read from SOCKS client.
     * @throws SocksException If a SOCKS protocol error occurred.
     * @throws IOException    If a I/O error occurred.
     */
    void doUDPAssociate(Session session, CommandMessage commandMessage) throws SocksException,
            IOException;

    /**
     * Sets session.
     *
     * @param session Session.
     */
    void setSession(Session session);

    /**
     * Returns method selector.
     *
     * @return Method selector.
     */
    MethodSelector getMethodSelector();

    /**
     * Sets a method selector.
     *
     * @param methodSelector A {@link MethodSelector} instance.
     */
    void setMethodSelector(MethodSelector methodSelector);

    /**
     * Returns buffer size.
     *
     * @return Buffer size.
     */
    int getBufferSize();

    /**
     * Sets buffer size.
     *
     * @param bufferSize buffer size.
     */
    void setBufferSize(int bufferSize);

    void setProxy(SocksProxy socksProxy);

    SocksProxyServer getSocksProxyServer();

    void setSocksProxyServer(SocksProxyServer socksProxyServer);

}
