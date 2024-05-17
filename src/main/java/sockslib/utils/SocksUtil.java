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
 * <code>SocksUtil</code> is a tool class.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Mar 24, 2015 4:16:22 PM
 */
public class SocksUtil {

    /**
     * Get bytes from an Integer.
     *
     * @param num an integer.
     * @return Bytes of an integer.
     */
    public static byte[] intTo2bytes(int num) {
        byte[] array = new byte[2];
        array[0] = (byte) ((num & 0xff00) >> 8);
        array[1] = (byte) (num & 0xff);
        return array;
    }

    /**
     * Returns the first byte of an integer.
     *
     * @param num an integer.
     * @return The first byte of an integer.
     */
    public static byte getFirstByteFromInt(int num) {
        return (byte) ((num & 0xff00) >> 8);
    }

    /**
     * Returns the second byte of an integer.
     *
     * @param num Port.
     * @return The second byte of an integer.
     */
    public static byte getSecondByteFromInt(int num) {
        return (byte) (num & 0xff);
    }

    /**
     * Get an integer from a byte array.
     *
     * @param bytes A byte array.
     * @return an integer.
     */
    public static int bytesToInt(byte[] bytes) {
        if (bytes.length != 2) {
            throw new IllegalArgumentException("byte array size must be 2");
        }
        return bytesToInt(bytes[0], bytes[1]);
    }

    /**
     * Returns a port.
     *
     * @param b1 First byte.
     * @param b2 Second byte.
     * @return an integer.
     */
    public static int bytesToInt(byte b1, byte b2) {
        return (UnsignedByte.toInt(b1) << 8) | UnsignedByte.toInt(b2);
    }

}
