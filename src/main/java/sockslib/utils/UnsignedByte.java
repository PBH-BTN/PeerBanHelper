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

package sockslib.utils;

/**
 * The class <code>UnsignedByte</code> is a tool to convert signed byte to unsigned byte.<br>
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Mar 20, 2015 9:24:58 AM
 */
public final class UnsignedByte {

    /**
     * the byte in signed value.
     */
    private byte num;

    /**
     * Constructs a UnsignedByte instance by an integer.
     *
     * @param num an integer.
     */
    public UnsignedByte(int num) {
        this.num = (byte) num;
    }

    /**
     * Constructs a UsingedByte instance by a byte.
     *
     * @param b A byte.
     */
    public UnsignedByte(byte b) {
        num = b;
    }

    /**
     * Gets unsigned byte from a signed byte.
     *
     * @param b signed byte.
     * @return unsigned byte as Integer.(0 ~ 255)
     */
    public static int toInt(byte b) {
        return b & 0xFF;
    }

    /**
     * Gets hex string of a byte.
     *
     * @param b byte
     * @return Byte as Hex string.
     */
    public static String toHexString(byte b) {
        return Integer.toHexString(toInt(b));
    }

    /**
     * Gets unsigned value as an integer.
     *
     * @return Unsigned value as an integer.
     */
    public int getUnsignedValue() {
        return num & 0xFF;
    }

    /**
     * Gets unsigned value as an integer.
     *
     * @return value as a normal Java byte.
     */
    public byte getSignedValue() {
        return num;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof UnsignedByte) {
            return num == ((UnsignedByte) obj).getSignedValue();
        }
        return false;
    }

    @Override
    public int hashCode() {
        return new Integer(num).hashCode();
    }

}
