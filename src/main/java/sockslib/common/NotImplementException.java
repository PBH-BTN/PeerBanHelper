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
 * The class <code>NotImplementException</code> represents a exception that will be threw when the
 * feature haven't implemented.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Apr 15, 2015 4:23:32 PM
 */
public class NotImplementException extends RuntimeException {


    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 1L;

    public NotImplementException() {
        super();
        // TODO Auto-generated constructor stub
    }

    public NotImplementException(String message, Throwable cause, boolean enableSuppression,
                                 boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
        // TODO Auto-generated constructor stub
    }

    public NotImplementException(String message, Throwable cause) {
        super(message, cause);
        // TODO Auto-generated constructor stub
    }

    public NotImplementException(String message) {
        super(message);
        // TODO Auto-generated constructor stub
    }

    public NotImplementException(Throwable cause) {
        super(cause);
        // TODO Auto-generated constructor stub
    }


}
