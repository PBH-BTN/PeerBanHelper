package com.ghostchu.peerbanhelper.util.asynctask.wrap;

import com.ghostchu.peerbanhelper.util.asynctask.AsyncTask;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class AsyncTaskWrappedOutputStream extends FilterOutputStream {

    private final AsyncTask asyncTask;

    public AsyncTaskWrappedOutputStream(OutputStream out, AsyncTask asyncTask) {
        super(out);
        this.asyncTask = asyncTask;
    }

    public AsyncTask getBgTask() {
        return asyncTask;
    }

    @Override
    public void write(int b) throws IOException {
        out.write(b);
        asyncTask.increment();
    }

    @Override
    public void write(byte[] b) throws IOException {
        out.write(b, 0, b.length);
        asyncTask.increment(b.length);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
        asyncTask.increment(len);
    }

    @Override
    public void flush() throws IOException {
        out.flush();
    }

    @Override
    public void close() throws IOException {
        out.close();
        asyncTask.close();
    }
}