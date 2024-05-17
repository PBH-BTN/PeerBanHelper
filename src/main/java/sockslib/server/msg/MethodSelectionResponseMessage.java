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

package sockslib.server.msg;

import sockslib.common.methods.SocksMethod;


/**
 * The class <code>MethodSelectionResponseMessage</code> represents response message for method
 * selection message. This message is always sent by SOCKS server.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Apr 6, 2015 11:10:05 AM
 */
public class MethodSelectionResponseMessage implements WritableMessage {

    /**
     * Version. 5 is default.
     */
    private int version = 5;

    /**
     * Selected method. 0xFF is default.
     */
    private int method = 0xFF;


    /**
     * Constructs an instance of {@link MethodSelectionResponseMessage}
     */
    public MethodSelectionResponseMessage() {

    }

    /**
     * Constructs an instance of {@link MethodSelectionResponseMessage} with a method.
     *
     * @param socksMethod Selected method.
     */
    public MethodSelectionResponseMessage(SocksMethod socksMethod) {
        this(5, socksMethod.getByte());
    }

    /**
     * Constructs an instance of {@link MethodSelectionResponseMessage} with a version and a method.
     *
     * @param version Version.
     * @param method  Value of selected method.
     */
    public MethodSelectionResponseMessage(int version, int method) {
        this.version = version;
        this.method = method;
    }

    /**
     * Constructs an instance of {@link MethodSelectionResponseMessage} with a version and a method.
     *
     * @param version     Version.
     * @param socksMethod Selected method.
     */
    public MethodSelectionResponseMessage(int version, SocksMethod socksMethod) {
        this(version, socksMethod.getByte());
    }

    @Override
    public byte[] getBytes() {
        byte[] bytes = new byte[2];
        bytes[0] = (byte) version;
        bytes[1] = (byte) method;
        return bytes;
    }

    @Override
    public int getLength() {
        return getBytes().length;
    }

    /**
     * Returns version.
     *
     * @return Version.
     */
    public int getVersion() {
        return version;
    }

    /**
     * Sets version.
     *
     * @param version Version.
     */
    public void setVersion(int version) {
        this.version = version;
    }

    /**
     * Return value of selected method.
     *
     * @return Value of selected method.
     */
    public int getMethod() {
        return method;
    }

    /**
     * Sets selected method with an integer.
     *
     * @param method Value of a method.
     */
    public void setMethod(int method) {
        this.method = method;
    }

}
