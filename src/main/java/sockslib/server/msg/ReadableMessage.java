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

package sockslib.server.msg;

import sockslib.common.SocksException;
import sockslib.server.Session;

import java.io.IOException;
import java.io.InputStream;

/**
 * The interface <code>ReadableMessage</code> represents a message that can be read by
 * {@link Session}.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Apr 5, 2015 10:35:12 AM
 */
public interface ReadableMessage extends Message {

    /**
     * Read message from a input stream.
     *
     * @param inputStream Input stream.
     * @throws SocksException If a SOCKS protocol error occurred.
     * @throws IOException    If an I/O error occurred.
     */
    void read(InputStream inputStream) throws SocksException, IOException;

}
