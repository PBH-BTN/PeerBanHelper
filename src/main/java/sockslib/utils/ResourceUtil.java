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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * The class <code>ResourceUtil</code> is used to close resources.
 *
 * @author Youchao Feng
 * @version 1.0
 * @since 1.0
 */
public class ResourceUtil {

    private static final Logger logger = LoggerFactory.getLogger(ResourceUtil.class);

    public static void close(InputStream inputStream, OutputStream outputStream, Socket socket) {
        close(inputStream, outputStream, socket, null);
    }

    public static void close(InputStream inputStream, OutputStream outputStream, Socket socket,
                             ServerSocket serverSocket) {
        close(inputStream);
        close(outputStream);
        close(socket);
        close(serverSocket);
    }

    public static void close(InputStream inputStream) {
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    public static void close(OutputStream outputStream) {
        if (outputStream != null) {
            try {
                outputStream.close();
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    public static void close(Socket socket) {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    public static void close(ServerSocket serverSocket) {
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    public static void close(DatagramSocket datagramSocket) {
        if (datagramSocket != null) {
            datagramSocket.close();
        }
    }
}
