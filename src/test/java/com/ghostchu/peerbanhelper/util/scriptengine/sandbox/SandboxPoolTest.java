package com.ghostchu.peerbanhelper.util.scriptengine.sandbox;

import com.ghostchu.peerbanhelper.util.scriptengine.sandbox.SandboxProtocol.Init;
import com.ghostchu.peerbanhelper.util.scriptengine.sandbox.SandboxProtocol.Metadata;
import com.ghostchu.peerbanhelper.util.scriptengine.sandbox.SandboxProtocol.Reply;
import com.google.gson.Gson;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(25)
class SandboxPoolTest {
    private static final Duration LONG = Duration.ofSeconds(10);
    private static final Duration SHORT = Duration.ofMillis(700);
    private static final Init LOCAL = new Init(".av", "sample", "healthy", "local");
    private final List<Process> children = new CopyOnWriteArrayList<>();
    private SandboxPool pool = new SandboxPool(this::launch, 1, LONG, LONG, LONG, Duration.ZERO);

    // A real child JVM exercises pipe backpressure, EOF, kill and exit acknowledgement.
    public static void main(String[] args) throws Exception {
        Init init = SandboxProtocol.read(System.in, Init.class);
        if (init.source().equals("EOF")) return;
        if (init.source().equals("compile forever")) Thread.sleep(60_000);
        String name = init.source().equals("invalid") ? null : init.filename();
        SandboxProtocol.write(System.out, new Metadata(name, "", "", true, false));
        if (init.source().equals("ignore stdin")) Thread.sleep(60_000);
        while (true) {
            var snapshot = SandboxProtocol.read(System.in, Map.class);
            if (snapshot.containsKey("hang")) Thread.sleep(60_000);
            if (snapshot.containsKey("EOF")) return;
            Object result = snapshot.containsKey("echo") ? snapshot.get("echo") : ProcessHandle.current().pid();
            SandboxProtocol.write(System.out, new Reply(result, snapshot.containsKey("fail") ? "failed" : null));
        }
    }

    private synchronized Process launch() throws IOException {
        String classPath = Stream.of(getClass(), SandboxProtocol.class, Gson.class)
                .map(type -> {
                    try {
                        return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
                    } catch (URISyntaxException ex) {
                        throw new AssertionError(ex);
                    }
                }).collect(Collectors.joining(File.pathSeparator));
        Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", classPath, getClass().getName()).start();
        children.add(child);
        return child;
    }

    @AfterEach
    void close() throws Exception {
        pool.reset();
        for (Process child : children) assertTrue(child.waitFor(5, SECONDS));
    }

    @Test
    void reusesOnlyIdenticalInit() throws Exception {
        assertEquals("sample", ((Metadata) pool.exchange(LOCAL, null)).name());
        Object first = pool.exchange(LOCAL, Map.of());
        assertEquals(first, pool.exchange(new Init(".av", "sample", "healthy", "local"), Map.of()));
        for (Init other : List.of(new Init(".av", "sample", "healthy", "btn"),
                new Init(".av", "sample", "changed", "btn"), new Init(".av", "renamed", "changed", "btn"),
                new Init(".js", "renamed", "changed", "btn"))) {
            Process previous = children.getLast();
            assertNotEquals(previous.pid(), pool.exchange(other, Map.of()));
            assertFalse(previous.isAlive());
        }
        assertEquals(5, children.size());
    }

    @Test
    void rejectsInvalidMetadataBeforePrepareOrExecution() {
        var invalid = new Init(".av", "sample", "invalid", "btn");
        for (Map<String, Object> snapshot : Arrays.<Map<String, Object>>asList(null, Map.of())) {
            var error = assertThrows(IllegalArgumentException.class, () -> pool.exchange(invalid, snapshot));
            assertEquals("Invalid script syntax", error.getMessage());
        }
        assertEquals(2, children.size());
        assertThrows(IOException.class, () -> pool.exchange(new Init(".av", "sample", "EOF", "btn"), null));
    }

    @Test
    void dropsBrokenWorkersWithoutReplayingRequests() throws Exception {
        for (String failure : List.of("fail", "EOF")) {
            int before = children.size();
            assertThrows(IOException.class, () -> pool.exchange(LOCAL, Map.of(failure, true)));
            assertEquals(before + 1, children.size());
            assertTrue(children.getLast().waitFor(5, SECONDS));
        }
        assertEquals("healthy", pool.exchange(LOCAL, Map.of("echo", "healthy")));
    }

    @Test
    void boundsCompileAndBothPipeDirections() throws Exception {
        pool = new SandboxPool(this::launch, 1, Duration.ofSeconds(3), SHORT, LONG, Duration.ZERO);
        assertThrows(IOException.class,
                () -> pool.exchange(new Init(".js", "sample", "compile forever", "btn"), null));
        assertTrue(children.getLast().waitFor(5, SECONDS));
        assertThrows(IOException.class, () -> pool.exchange(LOCAL, Map.of("hang", true)));
        assertTrue(children.getLast().waitFor(5, SECONDS));
        assertThrows(IOException.class, () -> pool.exchange(new Init(".js", "sample", "ignore stdin", "btn"),
                Map.of("payload", "x".repeat(900_000))));
        assertTrue(children.getLast().waitFor(5, SECONDS));
        assertEquals("ok", pool.exchange(LOCAL, Map.of("echo", "ok")));
    }

    @Test
    void stalledLaunchRetainsSlotAcrossTimeoutAndReset() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CompletableFuture<Void>();
        pool = new SandboxPool(() -> {
            entered.countDown();
            release.join(); // Model a native launch that cannot be interrupted.
            return launch();
        }, 1, SHORT, SHORT, LONG, Duration.ZERO);
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = threads.submit(() -> assertThrows(IOException.class, () -> pool.exchange(LOCAL, null)));
            try {
                assertTrue(entered.await(5, SECONDS));
                assertTrue(first.get(3, SECONDS).getMessage().contains("timed out"));
                pool.reset();
                assertThrows(IOException.class, () -> pool.exchange(LOCAL, null));
            } finally {
                release.complete(null);
            }
        }
        // The abandoned launch must die even though it returned after its caller left.
        long deadline = System.nanoTime() + SECONDS.toNanos(5);
        while (children.isEmpty() && System.nanoTime() < deadline) Thread.sleep(10);
        assertEquals(1, children.size());
        assertTrue(children.getFirst().waitFor(5, SECONDS));
    }

    @Test
    void concurrentClientsReceiveTheirOwnFramesWithinHardLimit() throws Exception {
        pool = new SandboxPool(this::launch, 2, LONG, LONG, LONG, Duration.ZERO);
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var replies = new ArrayList<Future<Object>>();
            for (int i = 0; i < 40; i++) {
                String value = "frame-" + i;
                replies.add(threads.submit(() -> pool.exchange(LOCAL, Map.of("echo", value))));
            }
            for (int i = 0; i < replies.size(); i++) assertEquals("frame-" + i, replies.get(i).get());
        }
        assertEquals(1, children.size());
    }

    @Test
    void differentScriptsFillThePoolAndQueueWithADeadline() throws Exception {
        var starting = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        pool = new SandboxPool(() -> {
            starting.countDown();
            try {
                if (!release.await(10, SECONDS)) throw new IOException("test gate timed out");
            } catch (InterruptedException ex) {
                throw new IOException(ex);
            }
            return launch();
        }, 2, LONG, SHORT, LONG, Duration.ZERO);
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var local = threads.submit(() -> pool.exchange(LOCAL, null));
            var btn = threads.submit(() -> pool.exchange(new Init(".av", "sample", "healthy", "btn"), null));
            try {
                assertTrue(starting.await(5, SECONDS));
                var third = new Init(".js", "other", "healthy", "local");
                assertThrows(IOException.class, () -> pool.exchange(third, null));
            } finally {
                release.countDown();
            }
            assertInstanceOf(Metadata.class, local.get());
            assertInstanceOf(Metadata.class, btn.get());
            assertEquals(2, children.size());
        }
    }

    @Test
    void idleWorkerExitsWithoutFurtherTraffic() throws Exception {
        pool = new SandboxPool(this::launch, 1, LONG, LONG, SHORT, Duration.ZERO);
        pool.exchange(LOCAL, null);
        assertTrue(children.getFirst().waitFor(5, SECONDS));
    }

    @Test
    void backsOffOnlyTheFailedInitAndPreservesSyntaxErrors() throws Exception {
        pool = new SandboxPool(this::launch, 2, LONG, SHORT, LONG, SHORT);
        var invalid = new Init(".js", "sample", "invalid", "btn");
        for (int i = 0; i < 4; i++) {
            var error = assertThrows(IllegalArgumentException.class, () -> pool.exchange(invalid, Map.of()));
            assertEquals("Invalid script syntax", error.getMessage());
        }
        assertEquals(1, children.size());
        assertThrows(IOException.class, () -> pool.exchange(LOCAL, Map.of("hang", true)));
        assertThrows(IOException.class, () -> pool.exchange(LOCAL, Map.of()));
        assertEquals(2, children.size());
        Thread.sleep(SHORT.plusMillis(100));
        assertEquals("ok", pool.exchange(LOCAL, Map.of("echo", "ok")));
        assertEquals(3, children.size());
    }

    @Test
    void holdsSlotUntilLauncherConfirmsCleanup() throws Exception {
        var cleaned = new CompletableFuture<Void>();
        pool = new SandboxPool(() -> {
            Process child = launch();
            return new Process() {
                public InputStream getInputStream() { return child.getInputStream(); }
                public InputStream getErrorStream() { return child.getErrorStream(); }
                public OutputStream getOutputStream() { return child.getOutputStream(); }
                public int waitFor() throws InterruptedException { return child.waitFor(); }
                public int exitValue() { return child.exitValue(); }
                public void destroy() { child.destroyForcibly(); }
                public CompletableFuture<Process> onExit() {
                    return child.onExit().thenCombine(cleaned, (p, ignored) -> this);
                }
            };
        }, 1, LONG, SHORT, LONG, Duration.ZERO);
        try {
            pool.exchange(LOCAL, null);
            pool.reset();
            assertTrue(children.getFirst().waitFor(5, SECONDS));
            var btn = new Init(".av", "sample", "healthy", "btn");
            assertThrows(IOException.class, () -> pool.exchange(btn, null));
            assertEquals(1, children.size());
        } finally {
            cleaned.complete(null);
        }
        assertEquals("ok", pool.exchange(LOCAL, Map.of("echo", "ok")));
    }
}
