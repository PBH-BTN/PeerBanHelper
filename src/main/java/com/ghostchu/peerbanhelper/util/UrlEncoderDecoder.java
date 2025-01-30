package com.ghostchu.peerbanhelper.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;

/**
 * @author 昆蟲_不在意 https://ccas.pixnet.net/blog/post/30650179
 */
@SuppressWarnings("all")
public final class UrlEncoderDecoder {
    private static final BitSet safeCharacters;   //http://www.java2s.com/Code/Java/Network-Protocol/ProvidesamethodtoencodeanystringintoaURLsafeform.htm
    private static final char[] hexadecimal = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    static {
        safeCharacters = new BitSet(256);
        int i;
        // 'lowalpha' rule
        for (i = 'a'; i <= 'z'; i++) {
            safeCharacters.set(i);
        }
        // 'hialpha' rule
        for (i = 'A'; i <= 'Z'; i++) {
            safeCharacters.set(i);
        }
        // 'digit' rule
        for (i = '0'; i <= '9'; i++) {
            safeCharacters.set(i);
        }

        // 'safe' rule
        safeCharacters.set('$');
        safeCharacters.set('-');
        safeCharacters.set('_');
        safeCharacters.set('.');
        safeCharacters.set('+');

        // 'extra' rule
        safeCharacters.set('!');
        safeCharacters.set('*');
        safeCharacters.set('\'');
        safeCharacters.set('(');
        safeCharacters.set(')');
        safeCharacters.set(',');

        // special characters common to http: file: and ftp: URLs ('fsegment' and 'hsegment' rules)
        safeCharacters.set('/');
        safeCharacters.set(':');
        safeCharacters.set('@');
        safeCharacters.set('&');
        safeCharacters.set('=');
    }

    public static String decodePath(String path) throws IOException {
        int maxBytesPerChar = 10;
        StringBuilder bufferPath = new StringBuilder(path);
        ByteArrayOutputStream decodePath = new ByteArrayOutputStream(maxBytesPerChar);

        for (int i = 0; i < bufferPath.length(); i++) {
            if (bufferPath.charAt(i) != '%') {
                decodePath.write(bufferPath.charAt(i));
            } else {
                char mixChar = (char) Integer.parseInt(bufferPath.substring(i + 1, i + 3), 16);
                i += 2;
                decodePath.write(mixChar);
            }
        }
        return decodePath.toString(StandardCharsets.UTF_8);
    }


    /**
     * Encode a path as required by the URL specification (<a href="http://www.ietf.org/rfc/rfc1738.txt">
     * RFC 1738</a>). This differs from <code>java.net.URLEncoder.encode()</code> which encodes according
     * to the <code>x-www-form-urlencoded</code> MIME format.
     *
     * @param path the path to encode
     * @return the encoded path
     */
    public static String encodePath(String path) {
        // stolen from org.apache.catalina.servlets.DefaultServlet ;)
        /*
         * Note: Here, ' ' should be encoded as "%20"
         * and '/' shouldn't be encoded.
         */
        int maxBytesPerChar = 10;
        StringBuilder rewrittenPath = new StringBuilder(path.length());
        ByteArrayOutputStream buf = new ByteArrayOutputStream(maxBytesPerChar);
        OutputStreamWriter writer;
        try {
            writer = new OutputStreamWriter(buf, StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
            writer = new OutputStreamWriter(buf);
        }

        for (int i = 0; i < path.length(); i++) {
            int c = path.charAt(i);
            if (safeCharacters.get(c)) {
                rewrittenPath.append((char) c);
            } else {
                // convert to external encoding before hex conversion
                try {
                    writer.write(c);
                    writer.flush();
                } catch (IOException e) {
                    buf.reset();
                    continue;
                }
                byte[] ba = buf.toByteArray();
                for (byte toEncode : ba) {
                    // Converting each byte in the buffer
                    rewrittenPath.append('%');
                    int low = (toEncode & 0x0f);
                    int high = ((toEncode & 0xf0) >> 4);

                    rewrittenPath.append(hexadecimal[high]);
                    rewrittenPath.append(hexadecimal[low]);
                }
                buf.reset();
            }
        }
        return rewrittenPath.toString();
    }


    public static String encodeToLegalPath(String path) {
        int maxBytesPerChar = 10;
        StringBuilder rewrittenPath = new StringBuilder(path.length());
        ByteArrayOutputStream buf = new ByteArrayOutputStream(maxBytesPerChar);
        OutputStreamWriter writer;
        try {
            writer = new OutputStreamWriter(buf, StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
            writer = new OutputStreamWriter(buf);
        }

        for (int i = 0; i < path.length(); i++) {
            int c = path.charAt(i);
            if (safeCharacters.get(c) || (char) c == '%') { //其實只加了這一小行，讓已經被encode的值不要再被encode了
                rewrittenPath.append((char) c);
            } else {
                try {
                    writer.write(c);
                    writer.flush();
                } catch (IOException e) {
                    buf.reset();
                    continue;
                }
                byte[] ba = buf.toByteArray();
                for (byte toEncode : ba) {
                    rewrittenPath.append('%');
                    int low = (toEncode & 0x0f);
                    int high = ((toEncode & 0xf0) >> 4);

                    rewrittenPath.append(hexadecimal[high]);
                    rewrittenPath.append(hexadecimal[low]);
                }
                buf.reset();
            }
        }
        return rewrittenPath.toString();
    }
}
