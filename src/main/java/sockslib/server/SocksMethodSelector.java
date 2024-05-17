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

import sockslib.common.methods.NoAcceptableMethod;
import sockslib.common.methods.SocksMethod;
import sockslib.server.msg.MethodSelectionMessage;

import java.util.HashSet;
import java.util.Set;

/**
 * The class <code>SocksMethodSelector</code> implements the {@link MethodSelector}.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Apr 7, 2015 10:24:43 AM
 * @see <a href="http://www.ietf.org/rfc/rfc1928.txt">SOCKS Protocol Version 5</a>
 */
public class SocksMethodSelector implements MethodSelector {

    /**
     * Methods that SOCKS server supports.
     */
    private Set<SocksMethod> supportMethods;

    /**
     * Constructs an instance of {@link SocksMethodSelector}.
     */
    public SocksMethodSelector() {
        supportMethods = new HashSet<>();
    }

    @Override
    public SocksMethod select(MethodSelectionMessage message) {
        int[] methods = message.getMethods();
        for (int i = 0; i < methods.length; i++) {
            for (SocksMethod method : supportMethods) {
                if (method.getByte() == methods[i]) {
                    return method;
                }
            }
        }
        return new NoAcceptableMethod();
    }

    @Override
    public Set<SocksMethod> getSupportMethods() {
        return supportMethods;
    }

    @Override
    public void setSupportMethods(Set<SocksMethod> supportMethods) {
        this.supportMethods = supportMethods;
    }

    @Override
    public void removeSupportMethod(SocksMethod socksMethod) {
        supportMethods.remove(socksMethod);
    }

    @Override
    public void clearAllSupportMethods() {
        supportMethods.clear();
    }

    @Override
    public void addSupportMethod(SocksMethod socksMethod) {
        supportMethods.add(socksMethod);
    }

    @Override
    public void setSupportMethod(SocksMethod... methods) {
        supportMethods.clear();
        for (int i = 0; i < methods.length; i++) {
            supportMethods.add(methods[i]);
        }

    }


}
