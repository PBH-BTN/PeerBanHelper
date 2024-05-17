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
import sockslib.common.NotImplementException;
import sockslib.common.SocksException;
import sockslib.server.Session;

import java.io.IOException;

/**
 * The class <code>GssApiMethod</code> is represents GSS API method in SOCKS protocol.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Mar 19, 2015 2:45:37 PM
 */
public class GssApiMethod extends AbstractSocksMethod {

    @Override
    public final int getByte() {
        return 0x01;
    }

    @Override
    public void doMethod(SocksProxy socksProxy) throws SocksException {
        // TODO implements later.
        throw new NotImplementException();
    }

    @Override
    public void doMethod(Session session) throws SocksException, IOException {
        // TODO implements later.
        throw new NotImplementException();
    }

    @Override
    public String getMethodName() {
        return "GSS API";
    }

}
