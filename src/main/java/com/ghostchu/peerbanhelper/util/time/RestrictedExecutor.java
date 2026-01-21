package com.ghostchu.peerbanhelper.util.time;

import com.ghostchu.peerbanhelper.text.Lang;
import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;
import java.util.function.Supplier;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public class RestrictedExecutor {
    public static <T> RestrictedExecResult<T> execute(String name, long timeout, Supplier<T> target) {
        CompletableFuture<T> sandbox = CompletableFuture.supplyAsync(target, Executors.newThreadPerTaskExecutor(Thread.ofPlatform().name("Restricted Executor [" + name + "]").factory()));
        try {
            return new RestrictedExecResult<>(false, sandbox.get(timeout, TimeUnit.MILLISECONDS));
        } catch (TimeoutException e) {
            return new RestrictedExecResult<>(true, null);
        } catch (InterruptedException e) {
            log.warn(tlUI(Lang.THREAD_INTERRUPTED), e);
            Thread.currentThread().interrupt();
            return new RestrictedExecResult<>(false, null);
        } catch (ExecutionException e) {
            Sentry.captureException(e);
            throw new RuntimeException(e);
        }
    }
}
