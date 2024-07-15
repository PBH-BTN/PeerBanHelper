package io.github.szabogabriel.jscgi.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class SCGIUtil {

    public static Map<String, String> parseHeaders(byte[] headers) {
        Map<String, String> ret = new HashMap<>();

        boolean parsingKey = true;
        String key = "";
        StringBuilder value = new StringBuilder();
        for (byte header : headers) {
            if (parsingKey) {
                if (header != 0) {
                    key += (char) header;
                } else {
                    parsingKey = false;
                }
            } else {
                if (header != 0) {
                    value.append((char) header);
                } else {
                    parsingKey = true;
                    if (ret.containsKey(key)) {
                        value.insert(0, ret.get(key) + ", ");
                    }
                    ret.put(key, value.toString());
                    key = "";
                    value = new StringBuilder();
                }
            }
        }

        return ret;
    }

    public static Map<String, String> parseHeadersOld(byte[] headers) {
        Map<String, String> ret = new HashMap<>();

        boolean parsingKey = true;
        int from = 0, keyDivider = 0, valDivider = 0, keyLength = 0, valLength = 0;
        byte[] key = {}, value = {};
        for (int i = 0; i < headers.length; i++) {
            if (parsingKey) {
                if (headers[i] != 0) {
                    keyDivider = i;
                } else {
                    parsingKey = false;
                    keyLength = keyDivider - from + 1;
                    key = new byte[keyLength];
                    System.arraycopy(headers, from, key, 0, keyLength);
                    from = i + 1;
                }
            } else {
                if (headers[i] != 0) {
                    valDivider = i;
                } else {
                    parsingKey = true;
                    if (valDivider >= from) {
                        valLength = valDivider - from + 1;
                        value = new byte[valLength];
                        System.arraycopy(headers, from, value, 0, valLength);
                        ret.put(new String(key), new String(value));
                    } else {
                        ret.put(new String(key), "");
                    }
                    from = i + 1;
                }
            }
        }

        return ret;
    }

    public static byte[] createHeaders(Map<String, String> headers) {
        ByteArrayOutputStream ret = new ByteArrayOutputStream();

        for (String key : headers.keySet()) {
            try {
                ret.write(key.getBytes());
                ret.write(0);
                String value = headers.get(key);
                if (value != null) {
                    ret.write(value.getBytes());
                }
                ret.write(0);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return ret.toByteArray();
    }

}
