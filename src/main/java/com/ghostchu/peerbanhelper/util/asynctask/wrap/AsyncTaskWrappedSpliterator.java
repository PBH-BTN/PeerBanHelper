package com.ghostchu.peerbanhelper.util.asynctask.wrap;

import com.ghostchu.peerbanhelper.util.asynctask.AsyncTask;

import java.util.*;
import java.util.function.Consumer;

/**
 * Any spliterator whose parallel iteration is tracked by a multi-threaded progress bar.
 * @author Tongfei Chen
 * @since 0.7.2
 */
public class AsyncTaskWrappedSpliterator<T> implements Spliterator<T>, AutoCloseable {

    private final Spliterator<T> underlying;
    private final AsyncTask asyncTask;
    private final Set<Spliterator<T>> openChildren;

    public AsyncTaskWrappedSpliterator(Spliterator<T> underlying, AsyncTask asyncTask) {
        this(underlying, asyncTask, Collections.synchronizedSet(new HashSet<>())); // has to be synchronized
    }

    private AsyncTaskWrappedSpliterator(Spliterator<T> underlying, AsyncTask asyncTask, Set<Spliterator<T>> openChildren) {
        this.underlying = underlying;
        this.asyncTask = asyncTask;
        this.openChildren = openChildren;
        this.openChildren.add(this);
    }

    public AsyncTask getBgTask() {
        return asyncTask;
    }

    @Override
    public void close() {
        asyncTask.close();
    }

    private void registerChild(Spliterator<T> child) {
        openChildren.add(child);
    }

    private void removeThis() {
        openChildren.remove(this);
        if (openChildren.isEmpty()) close();
        // only closes the progressbar if no spliterator is working anymore
    }

    @Override
    public boolean tryAdvance(Consumer<? super T> action) {
        boolean r = underlying.tryAdvance(action);
        if (r) asyncTask.increment();
        else removeThis();
        return r;
    }

    @Override
    public Spliterator<T> trySplit() {
        Spliterator<T> u = underlying.trySplit();
        if (u != null) {
            AsyncTaskWrappedSpliterator<T> child = new AsyncTaskWrappedSpliterator<>(u, asyncTask, openChildren);
            registerChild(child);
            return child;
        } else return null;
    }

    @Override
    public long estimateSize() {
        return underlying.estimateSize();
    }

    @Override
    public int characteristics() {
        return underlying.characteristics();
    }

    @Override // if not overridden, may return null since that is the default Spliterator implementation
    public Comparator<? super T> getComparator() {
        return underlying.getComparator();
    }

}