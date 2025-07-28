package com.ghostchu.peerbanhelper.util.traversal.forwarder;

import java.io.IOException;

public interface Forwarder extends AutoCloseable {
    void start() throws IOException;
}
