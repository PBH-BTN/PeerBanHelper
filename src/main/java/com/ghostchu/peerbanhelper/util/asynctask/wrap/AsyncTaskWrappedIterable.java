package com.ghostchu.peerbanhelper.util.asynctask.wrap;

import com.ghostchu.peerbanhelper.util.asynctask.AsyncTask;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

/**
 * Any iterable, when being iterated over, is tracked by a progress bar.
 * @author Tongfei Chen
 * @since 0.6.0
 */
public class AsyncTaskWrappedIterable<T> implements Iterable<T> {

    private final Iterable<T> underlying;
    private final AsyncTask asyncTask;

    public AsyncTaskWrappedIterable(Iterable<T> underlying, AsyncTask asyncTask) {
        this.underlying = underlying;
        this.asyncTask = asyncTask;
    }

    public AsyncTask getBgTask() {
        return asyncTask;
    }

    @Override
    public @NotNull AsyncTaskWrappedIterator<T> iterator() {
        Iterator<T> it = underlying.iterator();
        return new AsyncTaskWrappedIterator<>(
                it,
                asyncTask.setMax(underlying.spliterator().getExactSizeIfKnown())
                // getExactSizeIfKnown return -1 if not known, then indefinite progress bar naturally
        );
    }
}