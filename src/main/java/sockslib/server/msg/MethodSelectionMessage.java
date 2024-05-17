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

import sockslib.common.SocksException;

import java.io.IOException;
import java.io.InputStream;

import static sockslib.utils.StreamUtil.checkEnd;

/**
 * The class <code>MethodSelectionMessage</code> represents a method selection message.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Apr 5, 2015 10:47:05 AM
 */
public class MethodSelectionMessage implements ReadableMessage, WritableMessage {

    private int version;

    private int methodNum;

    private int[] methods;

    @Override
    public byte[] getBytes() {
        byte[] bytes = new byte[2 + methodNum];

        bytes[0] = (byte) version;
        bytes[1] = (byte) methodNum;
        for (int i = 0; i < methods.length; i++) {
            bytes[i + 2] = (byte) methods[i];
        }
        return bytes;
    }

    @Override
    public int getLength() {
        return getBytes().length;
    }


    @Override
    public void read(InputStream inputStream) throws SocksException, IOException {
        version = checkEnd(inputStream.read());
        methodNum = checkEnd(inputStream.read());
        methods = new int[methodNum];
        for (int i = 0; i < methodNum; i++) {
            methods[i] = checkEnd(inputStream.read());
        }
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public int getMethodNum() {
        return methodNum;
    }

    public void setMethodNum(int methodNum) {
        this.methodNum = methodNum;
    }

    public int[] getMethods() {
        return methods;
    }

    public void setMethods(int[] methods) {
        this.methods = methods;
    }

}
