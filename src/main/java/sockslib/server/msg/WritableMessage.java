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

import sockslib.server.Session;

/**
 * The interface <code>WritableMessage</code> represents a message that can be written by
 * {@link Session}.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Apr 5, 2015 10:25:45 AM
 */
public interface WritableMessage extends Message {

    /**
     * Returns the byte array of the message.
     *
     * @return The byte array of the message.
     */
    byte[] getBytes();

}
