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

package sockslib.server.listener;

import sockslib.server.Session;
import sockslib.server.msg.CommandMessage;

/**
 * This interface <code>CommandListener</code> is a command listener.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Nov 10, 2015 4:59 PM
 */
@FunctionalInterface
public interface CommandListener {

    /**
     * When a client request a SOCKS5 command, this method will be called.
     *
     * @param session Current session.
     * @param message The {@link CommandMessage} sent to server.
     * @throws CloseSessionException
     */
    void onCommand(Session session, CommandMessage message) throws CloseSessionException;
}
