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

/**
 * The interface <code>SessionCloseListener</code> is a listener which can listen session closed
 * event.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Nov 10, 2015 5:00 PM
 */
@FunctionalInterface
public interface SessionCloseListener {
    /**
     * When a session closed, this method will be called by {@link sockslib.server.SocksHandler}.
     *
     * @param session Current session.
     */
    void onClose(Session session);
}
