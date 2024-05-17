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

package sockslib.utils;

import sockslib.client.SocksProxy;
import sockslib.client.SocksSocket;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

/**
 * The class <code>TCPTelnet</code> implements {@link Telnet} based on TCP protocol.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Oct 15, 2015 12:42 PM
 * @see Telnet
 * @see UDPTelnet
 */
public final class TCPTelnet implements Telnet {

    private TelnetSocketInitializer socketInitializer;

    private SocksProxy proxy;

    public TCPTelnet() {
    }

    public TCPTelnet(SocksProxy proxy) {
        this.proxy = proxy;
    }

    @Override
    public byte[] request(final byte[] outputBytes, final String host, final int port)
            throws IOException {
        return request(outputBytes, new InetSocketAddress(host, port));
    }

    @Override
    public byte[] request(final byte[] outputBytes, final SocketAddress address)
            throws IOException {
        InputStream inputStream = null;
        OutputStream outputStream = null;
        ByteArrayOutputStream cache = null;
        Socket socket = null;
        if (proxy != null) {
            socket = new SocksSocket(proxy);
        } else {
            socket = new Socket();
        }
        byte[] response = null;
        IOException exception = null;
        try {
            if (socketInitializer != null) {
                socket = socketInitializer.init(socket);
            }
            socket.connect(address);
            outputStream = new BufferedOutputStream(socket.getOutputStream());
            inputStream = new BufferedInputStream(socket.getInputStream());
            outputStream.write(outputBytes);
            outputStream.flush();
            cache = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024 * 5];
            int length = 0;
            while ((length = inputStream.read(buffer)) > 0) {
                cache.write(buffer, 0, length);
            }
            response = cache.toByteArray();
        } catch (IOException e) {
            exception = e;
        } finally {
            ResourceUtil.close(inputStream);
            ResourceUtil.close(outputStream);
            ResourceUtil.close(socket);
        }
        if (exception != null) {
            throw exception;
        }
        return response;
    }

    public TelnetSocketInitializer getSocketInitializer() {
        return socketInitializer;
    }

    public void setSocketInitializer(final TelnetSocketInitializer socketInitializer) {
        this.socketInitializer = socketInitializer;
    }

    public SocksProxy getProxy() {
        return proxy;
    }

    public void setProxy(SocksProxy proxy) {
        this.proxy = proxy;
    }
}
