package com.ghostchu.peerbanhelper.util.time;

import com.ghostchu.peerbanhelper.text.Lang;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public final class TimeoutProtect implements AutoCloseable {
    @Getter
    private final String name;
    @Getter
    private final long timeRestrict;
    @Getter
    private boolean timeout;
    @Getter
    private List<Runnable> unfinishedTasks;
    private final Consumer<TimeoutProtect> timeoutCallback;

    // 记录已提交的 futures
    private final List<CompletableFuture<?>> futures = new ArrayList<>();

    public TimeoutProtect(String name, long timeRestrict, Consumer<TimeoutProtect> timeoutCallback) {
        this.name = name;
        this.timeRestrict = timeRestrict;
        this.timeoutCallback = timeoutCallback;
    }

    /**
     * 提交无返回值任务（使用共享 executor）
     */
    public void submit(Runnable task) {
        CompletableFuture<Void> cf = CompletableFuture.runAsync(task, RestrictedExecutor.getExecutorService());
        synchronized (futures) { futures.add(cf); }
    }

    /**
     * 提交有返回值任务并返回 CompletableFuture（可选使用）
     */
    public <T> CompletableFuture<T> submit(Supplier<T> supplier) {
        CompletableFuture<T> cf = CompletableFuture.supplyAsync(supplier, RestrictedExecutor.getExecutorService());
        synchronized (futures) { futures.add(cf); }
        return cf;
    }

    public void printUnfinishedTasks() {
        if (this.unfinishedTasks != null) {
            this.unfinishedTasks.forEach(r -> log.warn(tlUI(Lang.TIMING_UNFINISHED_TASK, r)));
        }
    }

    @Override
    public void close() {
        RestrictedExecResult<?> result = RestrictedExecutor.execute(this.name, this.timeRestrict, () -> {
            List<CompletableFuture<?>> snapshot;
            synchronized (futures) {
                snapshot = new ArrayList<>(futures);
            }
            for (CompletableFuture<?> f : snapshot) {
                try {
                    // 使用 get()，以便捕获 InterruptedException 并让上层检测到中断
                    f.get();
                } catch (InterruptedException ie) {
                    // 将 InterruptedException 包装为运行时异常向上抛，以便 RestrictedExecutor 能识别并处理
                    throw new RuntimeException(ie);
                } catch (ExecutionException ee) {
                    // 任务本身抛出异常：记录并继续等待其它任务
                    log.error(tlUI(Lang.TIMING_UNFINISHED_TASK, f), ee.getCause());
                } catch (CancellationException ce) {
                    // 已取消，继续
                }
            }
            return null;
        });
        this.timeout = result.timeout();

        // 取消仍未完成的任务并记录占位 runnable（保留 tlUI 日志语义）
        List<Runnable> notFinished = new ArrayList<>();
        synchronized (futures) {
            for (CompletableFuture<?> f : futures) {
                if (!f.isDone()) {
                    f.cancel(true);
                    Runnable placeholder = () -> log.warn(tlUI(Lang.TIMING_UNFINISHED_TASK, f));
                    notFinished.add(placeholder);
                }
            }
        }
        this.unfinishedTasks = Collections.unmodifiableList(notFinished);

        if (this.timeout && this.timeoutCallback != null) {
            try {
                this.timeoutCallback.accept(this);
            } catch (Throwable t) {
                log.error("timeoutCallback threw", t);
            }
        }
        printUnfinishedTasks();
    }
}
