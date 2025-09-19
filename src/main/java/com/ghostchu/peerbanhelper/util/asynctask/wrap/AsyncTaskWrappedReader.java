package com.ghostchu.peerbanhelper.util.asynctask.wrap;

import com.ghostchu.peerbanhelper.util.asynctask.AsyncTask;

import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;

/**
 * A reader whose progress is tracked by a progress bar.
 * @since 0.9.2
 * @author Tongfei Chen
 */
public class AsyncTaskWrappedReader extends FilterReader {

    private final AsyncTask asyncTask;
    private long mark = 0;

    public AsyncTaskWrappedReader(Reader in, AsyncTask asyncTask) {
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
    public int read(char[] b) throws IOException {
        int r = in.read(b);
        if (r != -1) asyncTask.increment(r);
        return r;
    }

    @Override
    public int read(char[] b, int off, int len) throws IOException {
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
    public void mark(int readAheadLimit) throws IOException {
        in.mark(readAheadLimit);
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