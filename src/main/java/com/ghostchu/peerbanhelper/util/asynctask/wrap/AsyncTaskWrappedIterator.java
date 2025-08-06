package com.ghostchu.peerbanhelper.util.asynctask.wrap;

import com.ghostchu.peerbanhelper.util.asynctask.AsyncTask;

import java.util.Iterator;

/**
 * Any iterator whose iteration is tracked by a progress bar.
 * @author Tongfei Chen
 * @since 0.6.0
 */
public class AsyncTaskWrappedIterator<T> implements Iterator<T>, AutoCloseable {

    private final Iterator<T> underlying;
    private final AsyncTask asyncTask;

    public AsyncTaskWrappedIterator(Iterator<T> underlying, AsyncTask pb) {
        this.underlying = underlying;
        this.asyncTask = pb;
    }

    public AsyncTask getBgTask() {
        return asyncTask;
    }

    @Override
    public boolean hasNext() {
        boolean r = underlying.hasNext();
        if (!r) asyncTask.close();
        return r;
    }

    @Override
    public T next() {
        T r = underlying.next();
        asyncTask.increment();
        return r;
    }

    @Override
    public void remove() {
        underlying.remove();
    }

    @Override
    public void close() {
        asyncTask.close();
    }
}