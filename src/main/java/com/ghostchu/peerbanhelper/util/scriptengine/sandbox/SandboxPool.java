package com.ghostchu.peerbanhelper.util.scriptengine.sandbox;

import com.ghostchu.peerbanhelper.util.scriptengine.sandbox.SandboxProtocol.Init;
import com.ghostchu.peerbanhelper.util.scriptengine.sandbox.SandboxProtocol.Metadata;
import com.ghostchu.peerbanhelper.util.scriptengine.sandbox.SandboxProtocol.Reply;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

public final class SandboxPool {
    interface Launcher {
        Process start() throws IOException;
    }

    private static final SandboxPool POOL = new SandboxPool(SandboxLauncher::start,
            Math.clamp(Integer.getInteger("pbh.script.sandbox.max-workers", 6), 1, 16),
            Duration.ofSeconds(60), Duration.ofSeconds(5), Duration.ofMinutes(2), Duration.ofSeconds(10));

    public static Metadata prepare(Init init) throws IOException {
        return (Metadata) POOL.exchange(init, null);
    }

    public static Object execute(Init init, Map<String, Object> snapshot) throws IOException {
        return POOL.exchange(init, Objects.requireNonNull(snapshot));
    }

    public static void closeAll() {
        POOL.reset();
    }

    private final Launcher launcher;
    private final int limit;
    private final long startup, execution, idle, backoff;
    // Access order gives idle eviction a least recently used candidate.
    private final LinkedHashMap<Init, Worker> workers = new LinkedHashMap<>(16, 0.75f, true);
    private record Failure(long retryAt, boolean syntax) {}
    private static final class InvalidSyntax extends IllegalArgumentException {
        InvalidSyntax() {
            super("Invalid script syntax");
        }
    }
    private final LinkedHashMap<Init, Failure> failures = new LinkedHashMap<>();
    private long generation;

    SandboxPool(Launcher launcher, int limit, Duration startup, Duration execution, Duration idle, Duration backoff) {
        this.launcher = launcher;
        this.limit = limit;
        this.startup = startup.toNanos();
        this.execution = execution.toNanos();
        this.idle = idle.toNanos();
        this.backoff = backoff.toNanos();
    }

    synchronized void reset() {
        generation++;
        failures.clear();
        for (Worker worker : workers.values()) worker.stop(new IOException("Sandbox pool closed"));
        notifyAll();
    }

    private static final class Request {
        final Map<String, Object> snapshot;
        final CompletableFuture<Long> ready = new CompletableFuture<>();
        final CompletableFuture<Object> result = new CompletableFuture<>();
        final long start = System.nanoTime();
        long executionBudget;

        Request(Map<String, Object> snapshot) {
            this.snapshot = snapshot;
        }
    }

    Object exchange(Init init, Map<String, Object> snapshot) throws IOException {
        var request = new Request(snapshot);
        Worker worker = null;
        try {
            worker = acquire(Objects.requireNonNull(init), request);
            long deadline = await(request.ready, request.start + startup);
            return await(request.result, deadline);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted waiting for sandbox", ex);
        } catch (TimeoutException ex) {
            throw new IOException("Sandbox timed out", ex);
        } finally {
            synchronized (this) {
                if (worker != null && worker.request == request && !worker.stopping) {
                    worker.fail(new IOException("Sandbox request cancelled"));
                }
            }
        }
    }

    private static <T> T await(CompletableFuture<T> future, long deadline)
            throws InterruptedException, TimeoutException, IOException {
        long left = deadline - System.nanoTime();
        if (left <= 0) throw new TimeoutException();
        try {
            return future.get(left, NANOSECONDS);
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof InvalidSyntax syntax) throw syntax;
            if (ex.getCause() instanceof IOException io) throw io;
            throw new IOException("Sandbox worker failed", ex.getCause());
        }
    }

    private synchronized Worker acquire(Init init, Request request)
            throws InterruptedException, TimeoutException, IOException {
        long current = generation;
        while (current == generation) {
            long left = request.start + execution - System.nanoTime();
            if (left <= 0) throw new TimeoutException();
            Failure failure = failures.get(init);
            if (failure != null) {
                if (failure.retryAt - System.nanoTime() > 0) {
                    if (failure.syntax) throw new InvalidSyntax();
                    throw new IOException("Sandbox worker is in retry backoff");
                }
                failures.remove(init);
            }
            Worker worker = workers.get(init);
            if (worker != null && !worker.stopping && worker.request == null) {
                if (!worker.process.isAlive()) {
                    var error = new IOException("Sandbox worker exited unexpectedly");
                    worker.fail(error);
                    throw error;
                }
                worker.request = request;
                request.ready.complete(request.start + execution);
                notifyAll();
                return worker;
            }
            if (worker == null && workers.size() < limit) {
                request.executionBudget = left;
                worker = new Worker(init, request);
                worker.thread.start();
                workers.put(init, worker);
                return worker;
            }
            if (worker == null && workers.values().stream().noneMatch(w -> w.stopping)) {
                workers.values().stream().filter(w -> w.request == null).findFirst()
                        .ifPresent(w -> w.stop(new IOException("Replacing idle sandbox")));
            }
            NANOSECONDS.timedWait(this, left);
        }
        throw new IOException("Sandbox pool closed");
    }

    private final class Worker implements Runnable {
        final Init init;
        final Thread thread = Thread.ofPlatform().daemon().name("sandbox-worker").unstarted(this);
        Request request;
        Process process;
        boolean stopping;
        CompletableFuture<Void> termination;

        Worker(Init init, Request request) {
            this.init = init;
            this.request = request;
        }

        void fail(Throwable failure) {
            if (!stopping) {
                failures.put(init, new Failure(System.nanoTime() + backoff, failure instanceof InvalidSyntax));
                if (failures.size() > 32) failures.pollFirstEntry();
            }
            stop(failure);
        }

        // Use the launcher's Process lifecycle: on Windows it must include container removal.
        void terminateProcess() {
            if (process != null && termination == null) {
                termination = CompletableFuture.runAsync(() -> {
                    process.destroyForcibly();
                    process.onExit().join();
                }, Thread::startVirtualThread);
            }
        }

        void stop(Throwable failure) {
            stopping = true;
            if (request != null) {
                request.ready.completeExceptionally(failure);
                request.result.completeExceptionally(failure);
                request = null;
            }
            if (process == null && thread != Thread.currentThread()) thread.interrupt();
            terminateProcess();
            SandboxPool.this.notifyAll();
        }

        @Override
        public void run() {
            try {
                synchronized (SandboxPool.this) {
                    if (stopping) return;
                }
                Process child = launcher.start();
                synchronized (SandboxPool.this) {
                    process = child;
                    if (stopping) return;
                }
                // Keep diagnostic output out of the protocol and out of unbounded buffers.
                Thread.ofVirtual().start(() -> {
                    try (var errors = child.getErrorStream()) {
                        errors.transferTo(OutputStream.nullOutputStream());
                    } catch (IOException ignored) {
                        // The pipe can close during forced termination.
                    }
                });
                SandboxProtocol.write(child.getOutputStream(), init);
                Metadata metadata = SandboxProtocol.read(child.getInputStream(), Metadata.class);
                if (metadata.name() == null) throw new InvalidSyntax();
                boolean cold = true;
                while (true) {
                    Request job;
                    long deadline;
                    synchronized (SandboxPool.this) {
                        long until = System.nanoTime() + idle;
                        while (!stopping && request == null) {
                            long left = until - System.nanoTime();
                            if (left <= 0) {
                                stopping = true;
                                return;
                            }
                            NANOSECONDS.timedWait(SandboxPool.this, left);
                        }
                        if (stopping) return;
                        job = request;
                        long now = System.nanoTime();
                        if (cold && now - job.start >= startup) throw new IOException("Sandbox startup timed out");
                        deadline = cold ? now + job.executionBudget : job.start + execution;
                        cold = false;
                        if (deadline - now <= 0) throw new IOException("Sandbox timed out");
                        job.ready.complete(deadline);
                    }
                    Object result = metadata;
                    if (job.snapshot != null) {
                        SandboxProtocol.write(child.getOutputStream(), job.snapshot);
                        Reply reply = SandboxProtocol.read(child.getInputStream(), Reply.class);
                        if (reply.error() != null) throw new IOException(reply.error());
                        result = reply.result();
                    }
                    synchronized (SandboxPool.this) {
                        if (stopping) return;
                        if (deadline - System.nanoTime() <= 0) throw new IOException("Sandbox timed out");
                        request = null;
                        job.result.complete(result);
                        SandboxPool.this.notifyAll();
                    }
                }
            } catch (Throwable failure) {
                synchronized (SandboxPool.this) {
                    fail(failure);
                }
            } finally {
                synchronized (SandboxPool.this) {
                    terminateProcess();
                }
                try {
                    if (termination != null) termination.join();
                    synchronized (SandboxPool.this) {
                        workers.remove(init);
                        SandboxPool.this.notifyAll();
                    }
                } catch (java.util.concurrent.CompletionException unconfirmed) {
                    System.getLogger(SandboxPool.class.getName()).log(System.Logger.Level.ERROR,
                            "Worker termination unconfirmed; keeping its sandbox slot reserved", unconfirmed);
                }
            }
        }
    }
}
