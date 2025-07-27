package com.ghostchu.peerbanhelper.util;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

@Slf4j
public class SocketCopyWorker implements Runnable, AutoCloseable {
    private final Socket from;
    private final Socket to;

    public SocketCopyWorker(Socket from, Socket to) {
        this.from = from;
        this.to = to;
    }

    public Thread start() {
        return Thread.ofVirtual()
                .name("SocketCopy-" + from.getRemoteSocketAddress() + " -> " + to.getRemoteSocketAddress())
                .start(this);
    }

    @Override
    public void run() {
        try {
            byte[] data = new byte[1024];
            InputStream is = from.getInputStream();
            OutputStream out = to.getOutputStream();
            while (from.isConnected() && !from.isClosed() && to.isConnected() && !to.isClosed()) {
                int len = is.read(data);
                if (len == -1) break;
                out.write(data, 0, len);
                out.flush();
            }
        } catch (IOException e) {
            log.debug("Forward socket from {} to {} is closed due exception", from, to, e);
            close();
        }
    }

    public void close() {
        try {
            from.close();
        } catch (IOException ignored) {
        } finally {
            try {
                to.close();
            } catch (IOException ignored) {
            }
        }
    }
}
