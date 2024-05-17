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

package sockslib.server.io;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;


/**
 * The class <code>SocketPipe</code> represents pipe that can transfer data from one socket to
 * another socket. The tow socket should be connected sockets. If any of the them occurred error the
 * pipe will close all of them.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Apr 15, 2015 10:46:03 AM
 */
public class SocketPipe implements Pipe {

    public static final String INPUT_PIPE_NAME = "INPUT_PIPE";
    public static final String OUTPUT_PIPE_NAME = "OUTPUT_PIPE";
    public static final String ATTR_SOURCE_SOCKET = "SOURCE_SOCKET";
    public static final String ATTR_DESTINATION_SOCKET = "DESTINATION_SOCKET";
    public static final String ATTR_PARENT_PIPE = "PARENT_PIPE";
    /**
     * Logger
     */
    protected static final Logger logger = LoggerFactory.getLogger(SocketPipe.class);
    /**
     * Pipe one.
     */
    private Pipe pipe1;

    /**
     * Pipe tow.
     */
    private Pipe pipe2;

    /**
     * Socket one.
     */
    private Socket socket1;

    /**
     * Socket two.
     */
    private Socket socket2;

    private String name;

    private Map<String, Object> attributes = new HashMap<>();

    /**
     * flag.
     */
    private boolean running = false;

    private PipeListener listener = new PipeListenerImp();

    /**
     * Constructs SocketPipe instance by tow connected sockets.
     *
     * @param socket1 A connected socket.
     * @param socket2 Another connected socket.
     * @throws IOException If an I/O error occurred.
     */
    public SocketPipe(Socket socket1, Socket socket2) throws IOException {
        this.socket1 = checkNotNull(socket1, "Argument [socks1] may not be null");
        this.socket2 = checkNotNull(socket2, "Argument [socks1] may not be null");
        pipe1 = new StreamPipe(socket1.getInputStream(), socket2.getOutputStream(), OUTPUT_PIPE_NAME);
        pipe1.setAttribute(ATTR_SOURCE_SOCKET, socket1);
        pipe1.setAttribute(ATTR_DESTINATION_SOCKET, socket2);
        pipe2 = new StreamPipe(socket2.getInputStream(), socket1.getOutputStream(), INPUT_PIPE_NAME);
        pipe2.setAttribute(ATTR_SOURCE_SOCKET, socket2);
        pipe2.setAttribute(ATTR_DESTINATION_SOCKET, socket1);

        pipe1.addPipeListener(listener);
        pipe2.addPipeListener(listener);
        pipe1.setAttribute(ATTR_PARENT_PIPE, this);
        pipe2.setAttribute(ATTR_PARENT_PIPE, this);
    }

    @Override
    public boolean start() {
        running = pipe1.start() && pipe2.start();
        return running;
    }

    @Override
    public boolean stop() {
        if (running) {
            // Ensure that isRunning() returns false when PipeListener.onStop() runs.
            running = false;
            pipe1.stop();
            pipe2.stop();
        }
        return running;
    }

    @Override
    public boolean close() {
        pipe2.removePipeListener(listener);
        pipe1.removePipeListener(listener);
        stop();
        try {
            if (socket1 != null && !socket1.isClosed()) {
                socket1.close();
            }
            if (socket2 != null && !socket2.isClosed()) {
                socket2.close();
            }
            return true;
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        return false;
    }

    @Override
    public int getBufferSize() {
        return 0;
    }

    @Override
    public void setBufferSize(int bufferSize) {
        pipe1.setBufferSize(bufferSize);
        pipe2.setBufferSize(bufferSize);
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public void addPipeListener(PipeListener pipeListener) {
        pipe1.addPipeListener(pipeListener);
        pipe2.addPipeListener(pipeListener);
    }

    @Override
    public void removePipeListener(PipeListener pipeListener) {

    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public void setAttribute(String name, Object value) {
        attributes.put(name, value);
    }

    @Override
    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }


    /**
     * The class <code>PipeListenerImp</code> is a pipe listener.
     *
     * @author Youchao Feng
     * @version 1.0
     * @date Apr 15, 2015 9:05:45 PM
     */
    private class PipeListenerImp implements PipeListener {

        @Override
        public void onStop(Pipe pipe) {
            StreamPipe streamPipe = (StreamPipe) pipe;
            logger.trace("Pipe[{}] stopped", streamPipe.getName());
            close();
        }

        @Override
        public void onStart(Pipe pipe) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onTransfer(Pipe pipe, byte[] buffer, int bufferLength) {
        }

        @Override
        public void onError(Pipe pipe, Exception exception) {
            // logger.info("{} {}", name, exception.getMessage());
        }

    }


}
