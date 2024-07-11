package io.github.szabogabriel.jscgi;

/**
 * Operation modes of the underlying application.
 * <p>
 * The STANDARD mode is compatible with most standard implementation of SCGI
 * servers and clients.
 * <p>
 * The SCGI_MESSAGE_BASED mode can be used to work entirely with
 * {@link SCGIMessage} classes on both the request
 * and the response levels. This method won't close the sockets opened
 * beforehand, thus providing a better performance with the tradeoff of not
 * being able to stream the data sent.
 *
 * @author gszabo
 */
public enum Mode {

    STANDARD, SCGI_MESSAGE_BASED,
    ;

}
