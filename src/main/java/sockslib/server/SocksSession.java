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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sockslib.common.Credentials;
import sockslib.common.SocksException;
import sockslib.common.net.MonitorSocketWrapper;
import sockslib.common.net.NetworkMonitor;
import sockslib.server.msg.ReadableMessage;
import sockslib.server.msg.WritableMessage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * The class <code>SocksSession</code> represents
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Apr 5, 2015 10:21:36 AM
 */
public class SocksSession implements Session {

    private static final Logger logger = LoggerFactory.getLogger(SocksSession.class);

    private Socket socket;

    private long id;

    private InputStream inputStream;

    private OutputStream outputStream;

    private Map<Long, Session> sessions;

    private SocketAddress clientAddress;

    private Map<Object, Object> attributes;

    private NetworkMonitor networkMonitor;

    private Credentials credentials;

    public SocksSession() {
    }

    public SocksSession(Socket socket) {
        this(0, socket, null);
    }

    public SocksSession(long id, Socket socket, Map<Long, Session> sessions) {
        if (!socket.isConnected()) {
            throw new IllegalArgumentException("Socket should be a connected socket");
        }
        if (socket instanceof MonitorSocketWrapper) {
            networkMonitor = new NetworkMonitor();
            ((MonitorSocketWrapper) socket).addMonitor(networkMonitor);
        }
        this.id = id;
        this.socket = socket;
        this.sessions = sessions;
        try {
            this.inputStream = this.socket.getInputStream();
            this.outputStream = this.socket.getOutputStream();
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        clientAddress = socket.getRemoteSocketAddress();

        attributes = new HashMap<Object, Object>();
    }

    @Override
    public Socket getSocket() {
        return socket;
    }

    @Override
    public void write(byte[] bytes) throws SocksException, IOException {
        write(bytes, 0, bytes.length);
    }

    @Override
    public void write(WritableMessage message) throws SocksException, IOException {
        write(message.getBytes());
    }

    @Override
    public int read(byte[] bytes) throws SocksException, IOException {
        return inputStream.read(bytes);
    }

    @Override
    public int read(ReadableMessage message) throws SocksException, IOException {
        message.read(inputStream);
        return message.getLength();
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public void close() {
        try {
            if (inputStream != null) {
                inputStream.close();
            }
            if (outputStream != null) {
                outputStream.close();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        } finally {
            sessions.remove(id);
        }
    }

    @Override
    public InputStream getInputStream() {
        return inputStream;
    }

    @Override
    public OutputStream getOutputStream() {
        return outputStream;
    }

    @Override
    public void write(byte[] bytes, int offset, int length) throws SocksException, IOException {
        outputStream.write(bytes, offset, length);
        outputStream.flush();
    }

    @Override
    public Map<Long, Session> getManagedSessions() {
        return sessions;
    }

    @Override
    public SocketAddress getClientAddress() {
        return clientAddress;
    }

    @Override
    public void setAttribute(Object key, Object value) {
        attributes.put(key, value);
    }

    @Override
    public Object getAttribute(Object key) {
        return attributes.get(key);
    }

    @Override
    public Map<Object, Object> getAttributes() {
        return attributes;
    }

    @Override
    public void clearAllAttributes() {
        attributes.clear();
    }

    @Override
    public boolean isClose() {
        try {
            socket.sendUrgentData(0);
            return false;
        } catch (IOException expected) {
            return true;
        }
    }

    @Override
    public boolean isConnected() {
        return socket.isConnected();
    }

    @Override
    public NetworkMonitor getNetworkMonitor() {
        return networkMonitor;
    }

    @Override
    public String toString() {
        return "SESSION[" + id + "]" + "@" + clientAddress;
    }

}
