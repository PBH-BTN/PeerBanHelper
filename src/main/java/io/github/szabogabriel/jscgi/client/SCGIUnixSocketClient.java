package io.github.szabogabriel.jscgi.client;

import io.github.szabogabriel.jscgi.Mode;
import io.github.szabogabriel.jscgi.SCGIMessage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.UnixDomainSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;

/**
 * A simple client implementation for the SCGI protocol.
 *
 * @author gszabo
 */
public class SCGIUnixSocketClient {

    private final SocketChannel socketChannel;
    private String host;
    private int port;

    private InputStream socketIn;
    private OutputStream socketOut;
    private byte[] buffer = new byte[2048];

    private Mode mode;

    /**
     * Create an SCGI lient.
     *
     * @param unixDomainSocketAddress The Unix Domain Socket Address
     * @param mode                    mode of the SCGI communication.
     */
    public SCGIUnixSocketClient(UnixDomainSocketAddress unixDomainSocketAddress, Mode mode) throws IOException {
        this.socketChannel = SocketChannel.open(unixDomainSocketAddress);
        this.mode = mode;
    }

    private void setup() throws IOException {
        this.socketIn = Channels.newInputStream(socketChannel);
        this.socketOut = Channels.newOutputStream(socketChannel);
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
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else {
            throw new IllegalStateException();
        }
    }

}
