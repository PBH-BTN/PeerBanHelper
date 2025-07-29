package com.ghostchu.peerbanhelper.util;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.function.Consumer;

@Slf4j
public class SocketCopyWorker implements Runnable, AutoCloseable {
    private final Socket from;
    private final Socket to;
    private final Consumer<Integer> trafficConsumer;
    private final Consumer<Exception> closeListener;
    private final Runnable trafficActivityRunnable;

    public SocketCopyWorker(Socket from, Socket to, @Nullable Consumer<Exception> connDisconnectListener, @Nullable Consumer<Integer> trafficConsumer, @Nullable Runnable onTrafficActivity) {
        this.from = from;
        this.to = to;
        this.closeListener = connDisconnectListener == null ? e -> {} : connDisconnectListener;
        this.trafficConsumer = trafficConsumer == null ? len -> {} : trafficConsumer;
        this.trafficActivityRunnable = onTrafficActivity == null ? () -> {
        } : onTrafficActivity;
    }

    public Thread startAsync() {
        return Thread.ofVirtual()
                .name("SocketCopy-" + from.getRemoteSocketAddress() + " -> " + to.getRemoteSocketAddress())
                .start(this);
    }

    public void startSync() {
        run();
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
                trafficConsumer.accept(len);
                trafficActivityRunnable.run();
            }
        } catch (IOException e) {
            log.debug("Forward socket from {} to {} is closed due exception", from, to, e);
            closeConnection();
            closeListener.accept(e);
        }
    }

    public void closeConnection() {
        try {
            from.close();
        } catch (IOException e) {
            log.debug("Failed to close from socket: {}", from, e);
        }
        try {
            to.close();
        } catch (IOException e) {
            log.debug("Failed to close to socket: {}", to, e);
        }
    }

    public void close() {
        closeConnection();
        closeListener.accept(null);
    }
}
