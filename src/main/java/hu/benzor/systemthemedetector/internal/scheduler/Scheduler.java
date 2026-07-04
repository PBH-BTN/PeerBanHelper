package hu.benzor.systemthemedetector.internal.scheduler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Scheduler {

    private static final long POLL_INTERVAL = 5000; // in ms

    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(Scheduler::daemonThreadFactory);
    private static final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    static {
        Runtime.getRuntime().addShutdownHook(
            new Thread(
                () -> {
                    scheduler.shutdownNow();
                    executor.shutdownNow();
                }
            )
        );
    }

    public static ScheduledFuture<?> schedule(Runnable task) {
        return scheduler.scheduleAtFixedRate(() -> executor.submit(task), 0, POLL_INTERVAL, TimeUnit.MILLISECONDS);
    }

    private static Thread daemonThreadFactory(Runnable r) {
        Thread thread = new Thread(r);
        thread.setDaemon(true);
        thread.setName("Scheduler-thread");
        return thread;
    }
}
