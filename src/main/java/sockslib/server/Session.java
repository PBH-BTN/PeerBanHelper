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

import sockslib.common.SocksException;
import sockslib.common.net.NetworkMonitor;
import sockslib.server.msg.ReadableMessage;
import sockslib.server.msg.WritableMessage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Map;

/**
 * The class <code>Session</code> represents a session between client with SOCKS server.
 * This class is simple encapsulation of java.net.Socket.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Apr 5, 2015 10:21:28 AM
 */
public interface Session {

    /**
     * Returns socket.
     *
     * @return socket that connected remote host.
     */
    Socket getSocket();

    /**
     * Writes bytes in output stream.
     *
     * @param bytes Bytes
     * @throws SocksException If a SOCKS protocol error occurred.
     * @throws IOException    If an I/O error occurred.
     */
    void write(byte[] bytes) throws SocksException, IOException;

    /**
     * Writes bytes in output stream.
     *
     * @param bytes  Bytes
     * @param offset Offset
     * @param length Bytes length.
     * @throws SocksException If a SOCKS protocol error occurred.
     * @throws IOException    If an I/O error occurred.
     */
    void write(byte[] bytes, int offset, int length) throws SocksException, IOException;

    /**
     * Writes <code>Message</code> in output stream.
     *
     * @param message {@link WritableMessage} instance.
     * @throws SocksException If a SOCKS protocol error occurred.
     * @throws IOException    If an I/O error occurred.
     */
    void write(WritableMessage message) throws SocksException, IOException;

    /**
     * Read a buffer.
     *
     * @param bytes Buffer which read in.
     * @return Read length
     * @throws SocksException If a SOCKS protocol error occurred.
     * @throws IOException    If an I/O error occurred.
     */
    int read(byte[] bytes) throws SocksException, IOException;

    /**
     * Reads a message.
     *
     * @param message a readable message.
     * @return Read bytes size.
     * @throws SocksException If a SOCKS protocol error occurred.
     * @throws IOException    If an I/O error occurred.
     */
    int read(ReadableMessage message) throws SocksException, IOException;

    /**
     * Gets session ID.
     *
     * @return session ID.
     */
    long getId();


    /**
     * Closes connection and removes itself from managed sessions.
     */
    void close();

    /**
     * Gets input stream.
     *
     * @return Input stream.
     */
    InputStream getInputStream();

    /**
     * Gets output stream.
     *
     * @return Output stream.
     */
    OutputStream getOutputStream();


    /**
     * Gets all sessions that be managed.
     *
     * @return All sessions.
     */
    Map<Long, Session> getManagedSessions();

    /**
     * Get remote host's IP address and port.
     *
     * @return Remote host's IP address and port.
     */
    SocketAddress getClientAddress();

    void setAttribute(Object key, Object value);

    Object getAttribute(Object key);

    /**
     * Returns all attributes.
     *
     * @return All attributes.
     */
    Map<Object, Object> getAttributes();

    /**
     * Clear all attributes in session.
     */
    void clearAllAttributes();

    /**
     * Returns <code>true</code> if the session is closed.
     *
     * @return If the session is closed, it returns <code>true</code>.
     */
    boolean isClose();

    /**
     * Returns <code>true</code> if the session is connected.
     *
     * @return If the session is connected returns <code>true</code>.
     */
    boolean isConnected();

    NetworkMonitor getNetworkMonitor();
}
