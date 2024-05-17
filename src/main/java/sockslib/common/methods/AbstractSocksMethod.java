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


/**
 * The class <code>AbstractSocksMethod</code> is a abstract class that implements
 * {@link SocksMethod}.
 * <p>
 * This class has override {@link #hashCode()} and {@link #equals(Object)} methods. Two methods are
 * same if the byte that return by {@link #getByte()} is equal.
 * </p>
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Mar 24, 2015 4:38:50 PM
 * @see SocksMethod
 */
public abstract class AbstractSocksMethod implements SocksMethod {

    @Override
    public int hashCode() {
        return new Integer(getByte()).hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof SocksMethod && ((SocksMethod) obj).getByte() == this.getByte();
    }

}
