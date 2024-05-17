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

package sockslib.common;


/**
 * The class <code>ProtocolErrorException</code> will be threw when there is a SOCKS protocol error.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Apr 18, 2015 11:19:50 PM
 */
public class ProtocolErrorException extends SocksException {

    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Constructs an instance of {@link ProtocolErrorException} with a message.
     *
     * @param msg Message.
     */
    public ProtocolErrorException(String msg) {
        super(msg);
    }

    public ProtocolErrorException() {
        this("Protocol error");
    }

}
