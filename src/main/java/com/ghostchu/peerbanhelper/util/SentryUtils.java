package com.ghostchu.peerbanhelper.util;

import io.sentry.ScopesAdapter;
import io.sentry.SentryOptions;
import io.sentry.SentryStackTraceFactory;
import io.sentry.protocol.SentryStackTrace;
import io.sentry.protocol.SentryThread;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SentryUtils {
    @SuppressWarnings("UnstableApiUsage")
    public static @NotNull List<SentryThread> getSentryThreads() {
        SentryOptions options = ScopesAdapter.getInstance().getOptions();
        SentryStackTraceFactory stackFactory = new SentryStackTraceFactory(options);
        List<SentryThread> sentryThreads = new ArrayList<>();
        // 获取当前 JVM 所有的线程和堆栈
        Map<Thread, StackTraceElement[]> allStackTraces = Thread.getAllStackTraces();
        for (Map.Entry<Thread, StackTraceElement[]> entry : allStackTraces.entrySet()) {
            Thread thread = entry.getKey();
            StackTraceElement[] stackTrace = entry.getValue();
            SentryThread sentryThread = new SentryThread();
            sentryThread.setName(thread.getName());
            sentryThread.setId(thread.threadId());
            sentryThread.setState(thread.getState().name());
            sentryThread.setCrashed(false); // Watchdog 触发通常不是崩溃，而是卡死
            // 标记触发 Watchdog 的那个线程（假设当前执行 hungry 的就是监控线程）
            sentryThread.setCurrent(Thread.currentThread() == thread);
            // 转换堆栈
            if (stackTrace.length > 0) {
                //noinspection UnstableApiUsage
                SentryStackTrace sentryStackTrace = new SentryStackTrace(
                        stackFactory.getStackFrames(stackTrace, false)
                );
                sentryThread.setStacktrace(sentryStackTrace);
            }
            sentryThreads.add(sentryThread);
        }
        return sentryThreads;
    }
}
