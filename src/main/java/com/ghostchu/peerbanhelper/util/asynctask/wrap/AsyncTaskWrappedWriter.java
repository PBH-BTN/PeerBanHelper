package com.ghostchu.peerbanhelper.util.asynctask.wrap;

import com.ghostchu.peerbanhelper.util.asynctask.AsyncTask;

import java.io.FilterWriter;
import java.io.IOException;
import java.io.Writer;

/**
 * A writer whose progress is tracked by a progress bar.
 * @since 0.9.3
 * @author Tongfei Chen
 */
public class AsyncTaskWrappedWriter extends FilterWriter {

    private final AsyncTask asyncTask;

    public AsyncTaskWrappedWriter(Writer out, AsyncTask asyncTask) {
        super(out);
        this.asyncTask = asyncTask;
    }

    @Override
    public void write(int c) throws IOException {
        out.write(c);
        asyncTask.increment();
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        out.write(cbuf, off, len);
        asyncTask.increment(len);
    }

    @Override
    public void write(String str, int off, int len) throws IOException {
        out.write(str, off, len);
        asyncTask.increment(len);
    }

    @Override
    public void write(String str) throws IOException {
        out.write(str);
        asyncTask.increment(str.length());
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