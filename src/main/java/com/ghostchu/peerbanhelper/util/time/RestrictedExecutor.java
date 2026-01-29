package com.ghostchu.peerbanhelper.util.time;

import com.ghostchu.peerbanhelper.text.Lang;
import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;
import java.util.function.Supplier;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public final class RestrictedExecutor {
    // JDK25 虚拟线程执行器（共享）
    private static final ExecutorService executorService =
            Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("RestrictedExecutor").factory());

    public static <T> RestrictedExecResult<T> execute(String name, long timeoutMillis, Supplier<T> target) {
        CompletableFuture<T> cf = CompletableFuture.supplyAsync(target, executorService);

        // 让 CompletableFuture 在超时后以 TimeoutException 完成（便于判定）
        cf.orTimeout(timeoutMillis, TimeUnit.MILLISECONDS);

        try {
            T res = cf.join();
            return new RestrictedExecResult<>(false, res);
        } catch (CompletionException ce) {
            Throwable cause = ce.getCause();
            // 超时或被取消：主动尝试取消以中断底层线程（尽早停掉）
            if (cause instanceof TimeoutException || cause instanceof CancellationException) {
                // 尝试中断正在运行的底层任务（如果还在运行）
                try { cf.cancel(true); } catch (Throwable ignore) { /* ignore */ }
                return new RestrictedExecResult<>(true, null);
            }
            if (containsCause(cause)) {
                log.warn(tlUI(Lang.THREAD_INTERRUPTED), cause);
                Thread.currentThread().interrupt();
                return new RestrictedExecResult<>(false, null);
            }
            Sentry.captureException(cause);
            throw new RuntimeException(cause);
        } catch (CancellationException cex) {
            try { cf.cancel(true); } catch (Throwable ignore) { /* ignore */ }
            return new RestrictedExecResult<>(true, null);
        } catch (Throwable t) {
            Sentry.captureException(t);
            throw new RuntimeException(t);
        }
    }

    private static boolean containsCause(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof InterruptedException) return true;
            cur = cur.getCause();
        }
        return false;
    }

    static ExecutorService getExecutorService() {
        return executorService;
    }
}
