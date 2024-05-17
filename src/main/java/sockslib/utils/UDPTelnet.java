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

import sockslib.client.Socks5DatagramSocket;
import sockslib.client.SocksProxy;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Arrays;

/**
 * The class <code>UDPTelnet</code> implements {@link Telnet} based on UDP protocol.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Oct 19, 2015 11:47 AM
 * @see Telnet
 * @see TCPTelnet
 */
public final class UDPTelnet implements Telnet {

    private SocksProxy proxy;

    public UDPTelnet() {

    }

    public UDPTelnet(SocksProxy proxy) {
        this.proxy = proxy;
    }

    @Override
    public byte[] request(final byte[] sendData, final String host, final int port) throws IOException {
        return request(sendData, new InetSocketAddress(host, port));
    }

    @Override
    public byte[] request(final byte[] sendData, final SocketAddress address) throws IOException {
        DatagramSocket socket = null;
        if (proxy != null) {
            socket = new Socks5DatagramSocket(proxy);
        } else {
            socket = new DatagramSocket();
        }
        socket.send(new DatagramPacket(sendData, sendData.length, address));
        byte[] buffer = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);
        return Arrays.copyOf(packet.getData(), packet.getLength());
    }

    public SocksProxy getProxy() {
        return proxy;
    }

    public void setProxy(SocksProxy proxy) {
        this.proxy = proxy;
    }
}
