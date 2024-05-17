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

/**
 * The class <code>PipeListener</code> represents a pipe listener.
 * <p>
 * You can add a {@link PipeListener} to a {@link Pipe} to monitor the {@link Pipe}.
 * </p>
 *
 * @author Youchao Feng
 * @version 1.0
 * @date May 3, 2015 1:37:10 AM
 */
public interface PipeListener {

    /**
     * This method will be called when the {@link Pipe} started.
     *
     * @param pipe The started {@link Pipe} instance.
     */
    void onStart(Pipe pipe);

    /**
     * This method will be called when the {@link Pipe} stopped.
     *
     * @param pipe The stopped {@link Pipe} instance.
     */
    void onStop(Pipe pipe);

    /**
     * This method will be called when the {@link Pipe} transferring data.
     *
     * @param pipe         {@link Pipe} instance.
     * @param buffer       Data which is transferring.
     * @param bufferLength length of data.
     */
    void onTransfer(Pipe pipe, byte[] buffer, int bufferLength);

    /**
     * This method will be called when an error occurred.
     *
     * @param pipe      {@link Pipe} instance.
     * @param exception The error that occurred in {@link Pipe}..
     */
    void onError(Pipe pipe, Exception exception);

}
