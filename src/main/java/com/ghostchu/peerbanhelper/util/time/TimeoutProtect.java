package com.ghostchu.peerbanhelper.util.time;

import com.ghostchu.peerbanhelper.text.Lang;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public final class TimeoutProtect implements AutoCloseable {
    @Getter
    private final ExecutorService service;
    @Getter
    private final long timeRestrict;
    @Getter
    private boolean timeout;
    @Getter
    private List<Runnable> unfinishedTasks;
    private Consumer<TimeoutProtect> timeoutCallback;

    public TimeoutProtect(long timeRestrict, ExecutorService service) {
        this.timeRestrict = timeRestrict;
        this.service = service;
    }

    public TimeoutProtect(long timeRestrict, ExecutorService service, Consumer<TimeoutProtect> timeoutCallback) {
        this.timeRestrict = timeRestrict;
        this.service = service;
        this.timeoutCallback = timeoutCallback;
    }

    public TimeoutProtect(long timeRestrict, Consumer<TimeoutProtect> timeoutCallback) {
        this.timeRestrict = timeRestrict;
        this.service = Executors.newVirtualThreadPerTaskExecutor();
        this.timeoutCallback = timeoutCallback;
    }


    public boolean isGracefullyShutdown() {
        return unfinishedTasks.isEmpty();
    }

    public void runIfTimeout(Consumer<TimeoutProtect> timeout) {
        if (this.timeout) {
            timeout.accept(this);
        }
        this.timeoutCallback = timeout;
    }

    public void printUnfinishedTasks() {
        if (this.unfinishedTasks != null) {
            this.unfinishedTasks.forEach(r -> {
                log.warn(tlUI(Lang.TIMING_UNFINISHED_TASK, r));
            });
        }
    }

    @Override
    public void close() {
        RestrictedExecResult<?> result = RestrictedExecutor.execute(this.timeRestrict, () -> {
            this.service.close();
            return null;
        });
        this.timeout = result.timeout();
        this.unfinishedTasks = this.service.shutdownNow();
        if (this.timeout) {
            timeoutCallback.accept(this);
        }
        printUnfinishedTasks();
    }
}
