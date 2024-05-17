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
 * The class <code>AddressType</code> represents type of address.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Apr 24, 2015 8:21:31 PM
 */
public class AddressType {

    /**
     * IP version 4 address.
     */
    public static final int IPV4 = 0x01;
    /**
     * Domain name.
     */
    public static final int DOMAIN_NAME = 0x03;

    /**
     * IP version 6 address.
     */
    public static final int IPV6 = 0x04;

    /**
     * A private constructor.
     */
    private AddressType() {
    }

    /**
     * Return <code>true</code> if type is supported.
     *
     * @param type Type of address.
     * @return If type is supported, it will return <code>true</code>.
     */
    public static boolean isSupport(int type) {
        return type == IPV4 || type == DOMAIN_NAME || type == IPV6;
    }

}
