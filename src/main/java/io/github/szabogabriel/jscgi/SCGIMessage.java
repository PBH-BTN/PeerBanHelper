package io.github.szabogabriel.jscgi;

import io.github.szabogabriel.jscgi.util.SCGIUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * The SCGIMessage class is the foundation for this SCGI implementation. It
 * holds, serializes and deserializes SCGI messages.
 *
 * @author gszabo
 */
public class SCGIMessage {

    private static final String CONTENT_LENGTH = "CONTENT_LENGTH";

    private Map<String, String> headers;
    private int bodyLengthInt = 0;
    private byte[] body;
    private InputStream socketIn;

    /**
     * Create an empty SCGI message without any headers or body.
     */
    public SCGIMessage() {
        this(new HashMap<>(), new byte[]{});
    }

    /**
     * Create an SCGI message with default values.
     *
     * @param headers a {@link Map} holding the HTTP headers.
     * @param body    a simple byte array as body.
     */
    public SCGIMessage(Map<String, String> headers, byte[] body) {
        if (headers == null || body == null)
            throw new NullPointerException();

        setHeaders(headers);
        setBody(body);
    }

    /**
     * An SCGI message can be created from an InputStream. This constructor is
     * useful used mainly by the underlying implementation of the client and the
     * server.
     *
     * @param in the {@link InputStream} to be used as source.
     * @throws IOException
     */
    public SCGIMessage(InputStream in) throws IOException {
        this();
        socketIn = in;

        int contentLength = contentLength();
        byte[] headersStream = read(contentLength);
        int comma = socketIn.read();

        if (comma == ',') {
            headers = SCGIUtil.parseHeaders(headersStream);

            String bodyLength = headers.get(CONTENT_LENGTH);
            if (bodyLength != null && bodyLength.length() > 0) {
                bodyLengthInt = Integer.parseInt(bodyLength);
            }
        }
    }

    /**
     * Adds a specific header to the SCGIMessage. It overrides the previous value if
     * present.
     *
     * @param key   of the header attribute to be added.
     * @param value of the header attribute to be added.
     */
    public void addHeader(String key, String value) {
        headers.put(key, value);
    }

    /**
     * Remove the header identified by the key. If not present, nothing will happen.
     *
     * @param key key of the header attribute to be removed.
     */
    public void removeHeader(String key) {
        headers.remove(key);
    }

    /**
     * Fetch the header attribute identified by the key.
     *
     * @param key of the header attribute to be returned.
     * @return the header attribute or {@code null} if not present.
     */
    public String getHeader(String key) {
        return headers.get(key);
    }

    /**
     * Returns all the headers present in the given SCGI message.
     *
     * @return {@link Map} holding the header attributes.
     */
    public Map<String, String> getHeaders() {
        return headers;
    }

    /**
     * Convenience method setting the {@link Map} holding the header
     * attributes. The previous header values (if any) will be overwritten.
     *
     * @param headers new header values.
     */
    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    /**
     * Fetch the curent SCGI message body.
     *
     * @return a byte array representing the body.
     */
    public byte[] getBody() {
        byte[] ret = {};

        try {
            ret = read(bodyLengthInt);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return ret;
    }

    /**
     * Set the new body value, if not null.
     *
     * @param body new body of the SCGI message.
     */
    public void setBody(byte[] body) {
        if (body != null) {
            this.body = body;
        }
    }

    /**
     * Fetch the stream from which the SCGI message was created. If the stream was
     * not used, it will return a null value.
     *
     * @return
     */
    public InputStream getBodyStream() {
        return socketIn;
    }

    /**
     * Checks whether a body is present in the SCGI message.
     *
     * @return
     */
    public boolean isBodyAvailable() {
        return bodyLengthInt > 0;
    }

    /**
     * Returns an integer representing the size of the body held by this SCGI
     * message.
     *
     * @return
     */
    public int getBodySize() {
        return bodyLengthInt;
    }

    /**
     * Write this SCGI message to the {@link OutputStream} provided as a
     * attribute.
     *
     * @param out
     * @throws IOException
     */
    public void serialize(OutputStream out) throws IOException {
        headers.put(CONTENT_LENGTH, "" + body.length);
        byte[] headerData = SCGIUtil.createHeaders(headers);
        byte[] length = Integer.toString(headerData.length).getBytes();

        out.write(length);
        out.write(58); // the character ':'
        out.write(headerData);
        out.write(44); // the character ','
        out.write(body);

    }

    private byte[] read(int length) throws IOException {
        byte[] buffer = new byte[length];
        socketIn.read(buffer, 0, length);
        return buffer;
    }

    private int contentLength() throws IOException {
        int ret = 0;
        int buf;

        while ((buf = socketIn.read()) != ':') {
            ret = (ret * 10) + (buf - '0');
        }

        return ret;
    }

}
