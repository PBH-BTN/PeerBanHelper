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

package sockslib.common.methods;

import sockslib.client.SocksProxy;
import sockslib.common.SocksException;
import sockslib.server.Session;

import java.io.IOException;


/**
 * The class <code>NoAuthenticationRequiredMethod</code> represents method which mean NO
 * AUTHENTICATION REQUIRED. This indicates that the server does not require the client to provide
 * authentication information.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Mar 17, 2015 11:34:01 AM
 * @see <a href="http://www.ietf.org/rfc/rfc1928.txt">SOCKS Protocol Version 5</a>
 */
public class NoAuthenticationRequiredMethod extends AbstractSocksMethod {

    @Override
    public final int getByte() {
        return 0x00;
    }

    @Override
    public void doMethod(SocksProxy socksProxy) throws SocksException, IOException {
        // Do nothing.
    }

    @Override
    public void doMethod(Session session) throws SocksException, IOException {
        // Do nothing

    }

    @Override
    public String getMethodName() {
        return "NO Authentication Required";
    }

}
