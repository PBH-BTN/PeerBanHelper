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

import java.util.Set;

/**
 * The class <code>MethodSelector</code> represents a method selector.<br>
 * This class will select one method from the methods that client given. If there is no method
 * acceptable, it will select {@link NoAcceptableMethod}.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Apr 7, 2015 10:17:12 AM
 */
public interface MethodSelector {


    /**
     * Selects a method form {@link MethodSelectionMessage}.It returns
     * {@link NoAcceptableMethod} if there is no acceptable method.
     *
     * @param message the message from client.
     * @return The method that server selected.
     */
    SocksMethod select(MethodSelectionMessage message);

    /**
     * Returns methods that server supported.
     *
     * @return The methods that server supported.
     */
    Set<SocksMethod> getSupportMethods();

    /**
     * Sets methods that server supported.
     *
     * @param supportMethods methods that server supported.
     */
    void setSupportMethods(Set<SocksMethod> supportMethods);

    /**
     * Clears all methods that server supported.
     */
    void clearAllSupportMethods();

    /**
     * Removes the method from the sets.
     *
     * @param socksMethod The method which will be removed.
     */
    void removeSupportMethod(SocksMethod socksMethod);

    /**
     * Adds a method into a support method list.
     *
     * @param socksMethod The method which will be supported.
     */
    void addSupportMethod(SocksMethod socksMethod);

    /**
     * Sets support methods.
     *
     * @param methods Methods supported.
     */
    void setSupportMethod(SocksMethod... methods);


}
