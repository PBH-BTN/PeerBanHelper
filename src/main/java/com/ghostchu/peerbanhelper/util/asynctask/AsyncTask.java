package com.ghostchu.peerbanhelper.util.asynctask;

import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.asynctask.wrap.*;
import com.google.common.collect.EvictingQueue;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.BaseStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Async task that can be used to track progress of a long-running operation.
 * @author ghost, tongfei (progressbar library)
 */
public class AsyncTask implements AutoCloseable {
    private final String taskId = UUID.randomUUID().toString();
    private TranslationComponent title = new TranslationComponent(Lang.BGTASK_TITLE_EMPTY);
    private TranslationComponent description;
    private final EvictingQueue<String> ringDeque = EvictingQueue.create(ExternalSwitch.parseInt("pbh.logger.ringDeque.size", 100));
    private final AtomicLong current = new AtomicLong(0);
    private final AtomicLong max = new AtomicLong(-1);
    private final List<WeakReference<AsyncTaskLoggerListener>> loggerListeners = Collections.synchronizedList(new ArrayList<>());
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public AsyncTask() {
        AsyncTaskManager.registerTask(this);
    }

    public void log(String text) {
        ringDeque.add(text);
        var it = loggerListeners.iterator();
        while (it.hasNext()) {
            var ref = it.next();
            var listener = ref.get();
            if (listener == null) {
                it.remove();
                continue;
            }
            listener.onLog(this, text);
        }
    }

    public List<String> getLogs() {
        return List.copyOf(ringDeque);
    }

    public TranslationComponent getTitle() {
        return title;
    }

    public AsyncTask setTitle(TranslationComponent title) {
        this.title = title;
        return this;
    }

    public TranslationComponent getDescription() {
        return description;
    }

    public AsyncTask setDescription(TranslationComponent description) {
        this.description = description;
        return this;
    }

    public long getCurrent() {
        return current.longValue();
    }

    public long getMax() {
        return max.longValue();
    }

    public AsyncTask setMax(long max) {
        this.max.set(max);
        return this;
    }

    public AsyncTask setCurrent(long current) {
        this.current.set(current);
        return this;
    }

    public AsyncTask increment(long increment) {
        this.current.addAndGet(increment);
        return this;
    }

    public AsyncTask increment() {
        this.current.incrementAndGet();
        return this;
    }

    public AsyncTask decrement(long decrement) {
        this.current.getAndUpdate(l -> l - decrement);
        return this;
    }

    public AsyncTask decrement() {
        this.current.getAndUpdate(l -> l - 1);
        return this;
    }

    public double getProgress() {
        long max = this.max.get();
        if (max <= 0) {
            return -1; // Indeterminate progress
        }
        return (double) this.current.get() / max;
    }

    public boolean isIndeterminate() {
        return this.max.get() < 0;
    }

    public static AsyncTask create() {
        return create(null);
    }

    public static AsyncTask create(TranslationComponent title) {
        var task = new AsyncTask();
        if (title != null) {
            task.setTitle(title);
        }
        return task;
    }

    public AsyncTask addLoggerListener(AsyncTaskLoggerListener listener) {
        this.loggerListeners.add(new WeakReference<>(listener));
        return this;
    }

    @NotNull
    public String getTaskId() {
        return taskId;
    }

    @Override
    public void close() {
        var it = loggerListeners.iterator();
        while (it.hasNext()) {
            var ref = it.next();
            var listener = ref.get();
            if (listener == null) {
                it.remove();
                continue;
            }
            listener.onTaskClose(this);
        }
        loggerListeners.clear();
        AsyncTaskManager.unregisterTask(this);
        closed.set(true);
    }

    public boolean isClosed() {
        return closed.get();
    }


    public static <T> Iterator<T> wrap(Iterator<T> it, TranslationComponent title) {
        return wrap(it, create(title).setMax(-1));
    }

    public static <T> Iterator<T> wrap(Iterator<T> it, AsyncTask asyncTask) {
        return new AsyncTaskWrappedIterator<>(it, asyncTask);
    }

    public static <T> Iterable<T> wrap(Iterable<T> ts, TranslationComponent title) {
        return wrap(ts, create(title));
    }

    public static <T> Iterable<T> wrap(Iterable<T> ts, AsyncTask asyncTask) {
        asyncTask.setMax(AsyncTaskUtil.getSpliteratorSize(ts.spliterator()));
        return new AsyncTaskWrappedIterable<>(ts, asyncTask);
    }

    public static InputStream wrap(InputStream is, TranslationComponent title) {
        return wrap(is, create(title));
    }

    public static InputStream wrap(InputStream is, AsyncTask asyncTask) {
        asyncTask.setMax(AsyncTaskUtil.getInputStreamSize(is));
        return new AsyncTaskWrappedInputStream(is, asyncTask);
    }

    public static OutputStream wrap(OutputStream os, TranslationComponent title) {
        return wrap(os, create(title));
    }

    public static OutputStream wrap(OutputStream os, AsyncTask asyncTask) {
        return new AsyncTaskWrappedOutputStream(os, asyncTask);
    }

    public static Reader wrap(Reader reader, TranslationComponent title) {
        return wrap(reader, create(title));
    }

    public static Reader wrap(Reader reader, AsyncTask pbb) {
        return new AsyncTaskWrappedReader(reader, pbb);
    }

    public static Writer wrap(Writer writer, TranslationComponent title) {
        return wrap(writer, create(title));
    }

    public static Writer wrap(Writer writer, AsyncTask asyncTask) {
        return new AsyncTaskWrappedWriter(writer, asyncTask);
    }

    public static <T> Spliterator<T> wrap(Spliterator<T> sp, TranslationComponent title) {
        return wrap(sp, create(title));
    }

    public static <T> Spliterator<T> wrap(Spliterator<T> sp, AsyncTask asyncTask) {
        asyncTask.setMax(AsyncTaskUtil.getSpliteratorSize(sp));
        return new AsyncTaskWrappedSpliterator<>(sp, asyncTask);
    }

    public static <T, S extends BaseStream<T, S>> Stream<T> wrap(S stream, TranslationComponent title) {
        return wrap(stream, create(title));
    }


    public static <T, S extends BaseStream<T, S>> Stream<T> wrap(S stream, AsyncTask asyncTask) {
        Spliterator<T> sp = wrap(stream.spliterator(), asyncTask);
        return StreamSupport.stream(sp, stream.isParallel());
    }

    public static <T> Stream<T> wrap(T[] array, TranslationComponent title) {
        return wrap(array, create(title));
    }

    public static <T> Stream<T> wrap(T[] array, AsyncTask asyncTask) {
        asyncTask.setMax(array.length);
        return wrap(Arrays.stream(array), asyncTask);
    }

    public EvictingQueue<String> getRingDeque() {
        return ringDeque;
    }
}
