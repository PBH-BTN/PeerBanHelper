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


import java.util.Map;

/**
 * The class <code>Pipe</code> represents a pipe that can transfer byte.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Apr 15, 2015 9:31:29 AM
 */
public interface Pipe {

    /**
     * Start the pipe, the pipe will work with a new thread.
     *
     * @return TODO
     */
    boolean start();

    /**
     * Stop the pipe, the pipe will stop transferring data.
     *
     * @return TODO
     */
    boolean stop();

    /**
     * Close pipe. if pipe is closed, it can't be started again.
     *
     * @return <code>true</code> if it closed.
     */
    boolean close();

    /**
     * GEts the buffer size.
     *
     * @return Buffer size.
     */
    int getBufferSize();

    /**
     * Sets buffer size.
     *
     * @param bufferSize Buffer size.
     */
    void setBufferSize(int bufferSize);

    /**
     * If the pipe is running.
     *
     * @return <code>true</code> if the pipe is running.
     */
    boolean isRunning();

    /**
     * Adds pipe listener.
     *
     * @param pipeListener Pipe listener.
     */
    void addPipeListener(PipeListener pipeListener);

    /**
     * Removes pipe listener.
     *
     * @param pipeListener Pipe listener.
     */
    void removePipeListener(PipeListener pipeListener);

    /**
     * Returns pipe's name.
     *
     * @return Name of pipe.
     */
    String getName();

    /**
     * Sets pipe's name.
     *
     * @param name Name of pipe.
     */
    void setName(String name);


    void setAttribute(String name, Object value);

    Object getAttribute(String name);

    Map<String, Object> getAttributes();
}
