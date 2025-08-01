package com.ghostchu.peerbanhelper.util.traversal.stun;

import java.io.IOException;
import java.net.Socket;
import java.net.StandardSocketOptions;

public class StunSocketTool {
    public static Socket getSocket() throws IOException {
        Socket socket = new Socket();
        if (socket.supportedOptions().contains(StandardSocketOptions.SO_REUSEPORT)) {
            socket.setOption(StandardSocketOptions.SO_REUSEPORT, true);
        }
        if (socket.supportedOptions().contains(StandardSocketOptions.SO_REUSEADDR)) {
            socket.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        }
        return socket;
    }
}
