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

import java.util.Iterator;

/**
 * The class <code>IPIterator</code> represents an IP address iterator.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date May 2, 2015 9:23:00 AM
 */
public class IPIterator implements Iterator<IP> {

    /**
     * IP address range.
     */
    private IPRange range;

    /**
     * Current IP address.
     */
    private IP currentIP;

    /**
     * A flag. It's always <code>true</code> in the beginning but it will become <code>false</code> if
     * {{@link #next()} is invoked.
     */
    private boolean start = true;

    /**
     * Constructs an instance of {@link IPIterator} with a {@link IPRange}.
     *
     * @param range IP address range.
     */
    public IPIterator(IPRange range) {
        this.range = range;
        currentIP = range.getStartIP();
    }

    /**
     * Constructs an instance of {@link IPIterator} with tow IP address.
     *
     * @param startIP Starting IP address.
     * @param endIP   End IP address.
     */
    public IPIterator(IP startIP, IP endIP) {
        range = new IPRange(startIP, endIP);
        currentIP = startIP;
    }

    @Override
    public boolean hasNext() {
        if (start) {
            return true;
        } else {
            return range.contains(currentIP.nextIP());
        }
    }

    @Override
    public IP next() {

        if (start) {
            start = false;
            return currentIP;
        } else {
            currentIP = currentIP.nextIP();
            return currentIP;
        }
    }

    @Override
    public void remove() {
    }

}
