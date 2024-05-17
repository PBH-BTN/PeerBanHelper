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

import com.google.common.base.Charsets;

import java.io.IOException;
import java.io.InputStream;

/**
 * The class <code>StreamUtil</code> is a tool class for stream.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Oct 20, 2015 2:55 PM
 */
public class StreamUtil {

    public static int checkEnd(int b) throws IOException {
        if (b < 0) {
            throw new IOException("End of stream");
        } else {
            return b;
        }
    }

    public static byte[] read(InputStream inputStream, int length) throws IOException {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) checkEnd(inputStream.read());
        }
        return bytes;
    }

    public static String readString(InputStream inputStream, int length) throws IOException {
        return new String(read(inputStream, length), Charsets.UTF_8);
    }
}
