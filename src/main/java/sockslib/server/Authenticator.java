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

package sockslib.server;

import sockslib.common.AuthenticationException;
import sockslib.common.Credentials;

/**
 * The class <code>Authenticator</code> represents an authenticator.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Apr 16, 2015 11:29:56 AM
 */
public interface Authenticator {

    /**
     * This method will do authentication work.
     *
     * @param credentials credentials.
     * @param session     Session.
     * @throws AuthenticationException If authentication is failed.
     */
    void doAuthenticate(Credentials credentials, Session session) throws AuthenticationException;

}
