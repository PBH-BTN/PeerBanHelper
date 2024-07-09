package io.github.szabogabriel.jscgi.client;

import io.github.szabogabriel.jscgi.Mode;
import io.github.szabogabriel.jscgi.SCGIMessage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * A simple client implementation for the SCGI protocol.
 *
 * @author gszabo
 */
public class SCGIClient {

    private String host;
    private int port;

    private Socket socket;

    private InputStream socketIn;
    private OutputStream socketOut;
    private byte[] buffer = new byte[2048];

    private Mode mode;

    /**
     * Create an SCGI client in the
     * {@link Mode.STANDARD} mode.
     *
     * @param host host of the SCGI server to connect to.
     * @param port port of the SCGI server to connect to.
     */
    public SCGIClient(String host, int port) {
        this(host, port, Mode.STANDARD);
    }

    /**
     * Create an SCGI lient.
     *
     * @param host host of the SCGI server to connect to.
     * @param port port of the SCGI server to connect to.
     * @param mode mode of the SCGI communication.
     */
    public SCGIClient(String host, int port, Mode mode) {
        this.host = host;
        this.port = port;
        this.mode = mode;
    }

    private void setup() throws IOException {
        if (socket == null || socket.isClosed()) {
            socket = new Socket(host, port);

            socketIn = socket.getInputStream();
            socketOut = socket.getOutputStream();
        }
    }

    /**
     * This method is only available for the
     * {@link Mode} SCGI_MESSAGE_BASED. It will fetch
     * the data into memory and return a new SCGI message instance.
     *
     * @param request
     * @return
     * @throws IOException
     */
    public SCGIMessage sendAndReceiveAsScgiMessage(SCGIMessage request) throws IOException {
        if (mode == Mode.SCGI_MESSAGE_BASED) {
            setup();
            request.serialize(socketOut);
            return new SCGIMessage(socketIn);
        } else {
            throw new IllegalStateException();
        }
    }

    /**
     * This method sends an SCGI request and returns the SCGI message as a byte
     * array. This method is only available for the
     * {@link Mode} STANDARD mode.
     *
     * @param request
     * @return
     * @throws UnknownHostException
     * @throws IOException
     */
    public byte[] sendAndReceiveAsByteArray(SCGIMessage request) throws UnknownHostException, IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        sendRequest(request, out);
        return out.toByteArray();
    }

    /**
     * This method sends an SCGI request and writes the answer into the
     * {@link OutputStream} provided as a parameter. This method is only
     * available for the {@link Mode} STANDARD mode.
     *
     * @param request
     * @return
     * @throws UnknownHostException
     * @throws IOException
     */
    public void sendRequest(SCGIMessage request, OutputStream response) throws UnknownHostException, IOException {
        if (mode == Mode.STANDARD) {
            setup();

            try {
                request.serialize(socketOut);

                int read;
                while ((read = socketIn.read(buffer)) > 0) {
                    response.write(buffer, 0, read);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    socketIn.close();
                    socketOut.close();
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else {
            throw new IllegalStateException();
        }
    }

}
