package com.ghostchu.peerbanhelper.util.asynctask;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Spliterator;

public class AsyncTaskUtil {
    static long getInputStreamSize(InputStream is) {
        try {
            if (is instanceof FileInputStream)
                return ((FileInputStream) is).getChannel().size();

            // estimate input stream size with InputStream::available
            int available = is.available();
            if (available > 0) return available;
        } catch (IOException ignored) {
        }
        return -1;
    }

    static <T> long getSpliteratorSize(Spliterator<T> sp) {
        try {
            long size = sp.estimateSize();
            return size != Long.MAX_VALUE ? size : -1;
        } catch (Exception ignored) {
        }
        return -1;
    }
}
