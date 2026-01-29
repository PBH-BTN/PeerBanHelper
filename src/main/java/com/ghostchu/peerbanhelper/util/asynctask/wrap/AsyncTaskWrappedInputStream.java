package com.ghostchu.peerbanhelper.util.asynctask.wrap;

import com.ghostchu.peerbanhelper.util.asynctask.AsyncTask;
import org.jetbrains.annotations.NotNull;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Any input stream whose progress is tracked by a progress bar.
 * @author Tongfei Chen
 * @since 0.7.0
 */
public class AsyncTaskWrappedInputStream extends FilterInputStream {

    private final AsyncTask asyncTask;
    private long mark = 0;

    public AsyncTaskWrappedInputStream(InputStream in, AsyncTask asyncTask) {
        super(in);
        this.asyncTask = asyncTask;
    }

    public AsyncTask getBgTask() {
        return asyncTask;
    }

    @Override
    public int read() throws IOException {
        int r = in.read();
        if (r != -1) asyncTask.increment();
        return r;
    }

    @Override
    public int read(@NotNull byte[] b) throws IOException {
        int r = in.read(b);
        if (r != -1) asyncTask.increment(r);
        return r;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int r = in.read(b, off, len);
        if (r != -1) asyncTask.increment(r);
        return r;
    }

    @Override
    public long skip(long n) throws IOException {
        long r = in.skip(n);
        asyncTask.increment(r);
        return r;
    }

    @Override
    public void mark(int readLimit) {
        in.mark(readLimit);
        mark = asyncTask.getCurrent();
    }

    @Override
    public void reset() throws IOException {
        in.reset();
        asyncTask.setCurrent(mark);
    }

    @Override
    public void close() throws IOException {
        in.close();
        asyncTask.close();
    }

}