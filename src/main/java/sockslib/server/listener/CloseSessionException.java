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

/**
 * <code>CloseSessionException</code> is an exception to request
 * {@link sockslib.server.SocksHandler} to close current session.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Nov 04, 2015 4:06 PM
 */
public class CloseSessionException extends Exception {

    public CloseSessionException() {
        super("Stop Process");
    }

    public CloseSessionException(Throwable cause) {
        super(cause);
    }

    public CloseSessionException(String message) {
        super(message);
    }

    public CloseSessionException(String message, Throwable cause) {
        super(message, cause);
    }

    protected CloseSessionException(String message, Throwable cause, boolean enableSuppression,
                                    boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
