package com.ghostchu.peerbanhelper.util.scriptengine.sandbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.WINDOWS)
class WindowsSandboxLauncherTest {
    private static final String PACKAGE = WindowsSandboxLauncher.class.getPackageName();
    private static final String WORKER = PACKAGE.replace('.', '/') + "/SandboxWorker.class";
    @TempDir Path release;

    @Test
    void rejectsExplodedClassesAndManifestPathsOutsideLibraries() throws Exception {
        assertThrows(IOException.class, WindowsSandboxLauncher::start);
        for (String entry : List.of("libraries/../data/private.jar", "file:///C:/private.jar",
                "libraries/x.jar,readonly=false", "libraries/subdir/x.jar")) {
            Path jar = release.resolve("PeerBanHelper.jar");
            try (var out = archive(jar, entry)) {
                out.putNextEntry(new JarEntry(WORKER));
            }
            assertThrows(IOException.class, () -> WindowsSandboxLauncher.createOptions(jar));
        }
    }

    @Test
    @Timeout(180)
    @EnabledIfEnvironmentVariable(named = "PBH_TEST_WINDOWS_RELEASE", matches = ".+")
    void realReleaseRunsAllWorkerEngines() throws Exception {
        Path jar = Path.of(System.getenv("PBH_TEST_WINDOWS_RELEASE")).resolve("PeerBanHelper.jar");
        try (var loader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            var start = loader.loadClass(WindowsSandboxLauncher.class.getName()).getMethod("start");
            for (String extension : List.of(".av", ".js", ".lua")) {
                Process worker = (Process) start.invoke(null);
                try {
                    Thread.startVirtualThread(() -> {
                        try (var errors = worker.getErrorStream()) {
                            errors.transferTo(OutputStream.nullOutputStream());
                        } catch (IOException ignored) {
                            // Closed on forced termination.
                        }
                    });
                    String source = extension.equals(".lua") ? "return 6 * 7" : "6 * 7";
                    SandboxProtocol.write(worker.getOutputStream(),
                            new SandboxProtocol.Init(extension, "smoke" + extension, source, "test"));
                    assertNotNull(SandboxProtocol.read(worker.getInputStream(), SandboxProtocol.Metadata.class).name());
                    SandboxProtocol.write(worker.getOutputStream(), Map.of());
                    var reply = SandboxProtocol.read(worker.getInputStream(), SandboxProtocol.Reply.class);
                    assertNull(reply.error());
                    assertEquals(42.0, assertInstanceOf(Number.class, reply.result()).doubleValue());
                    worker.getOutputStream().close();
                    assertEquals(0, worker.waitFor());
                } finally {
                    worker.destroyForcibly();
                }
            }
        }
    }

    @Test
    @Timeout(90)
    @EnabledIfEnvironmentVariable(named = "PBH_TEST_WINDOWS_SANDBOX", matches = "1")
    void packagedStdioIsolationAndCleanup() throws Exception {
        Path app = createFixture();
        try (var loader = new URLClassLoader(new URL[]{app.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            var start = loader.loadClass(WindowsSandboxLauncher.class.getName()).getMethod("start");
            for (boolean force : List.of(false, true)) {
                Process worker = (Process) start.invoke(null);
                try {
                    String request = force ? "hold" : "request";
                    worker.outputWriter().write(request + "\n");
                    worker.outputWriter().flush();
                    var reader = worker.inputReader();
                    assertEquals("reply:" + request, reader.readLine(), () -> {
                        worker.destroyForcibly();
                        try { return new String(worker.getErrorStream().readAllBytes()); }
                        catch (IOException e) { throw new AssertionError(e); }
                    });
                    String id = reader.readLine();
                    assertTrue(id.matches("[0-9a-f]{12}"));
                    if (force) {
                        worker.destroyForcibly();
                    } else {
                        worker.getOutputStream().close();
                    }
                    int exit = worker.waitFor();
                    if (!force) assertEquals(0, exit);
                    assertRemoved(id);
                } finally {
                    worker.destroyForcibly();
                }
            }
        }
    }

    @Test
    @Timeout(60)
    @EnabledIfEnvironmentVariable(named = "PBH_TEST_WINDOWS_SANDBOX", matches = "1")
    void unconfirmedRemovalFailsTheExitFuture() throws Exception {
        Path app = createFixture();
        try (var loader = new URLClassLoader(new URL[]{app.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            var start = loader.loadClass(WindowsSandboxLauncher.class.getName()).getMethod("start");
            Process worker = (Process) start.invoke(null);
            var config = worker.getClass().getDeclaredField("config");
            config.setAccessible(true);
            Path diagnostic = ((Path) config.get(worker)).resolve("stderr");
            try {
                worker.outputWriter().write("hold\n");
                worker.outputWriter().flush();
                assertEquals("reply:hold", worker.inputReader().readLine());
                String id = worker.inputReader().readLine();
                // Break only this worker's rm and ps commands. Leave Docker Desktop and other workers running.
                Files.delete(diagnostic);
                Files.createDirectory(diagnostic);
                worker.destroyForcibly();
                var failure = assertThrows(ExecutionException.class, () -> worker.onExit().get(10, TimeUnit.SECONDS));
                assertInstanceOf(java.io.UncheckedIOException.class, failure.getCause());
                assertTrue(worker.isAlive());
                assertThrows(IllegalStateException.class, worker::waitFor);
                Files.delete(diagnostic);
                var cleanup = worker.getClass().getDeclaredMethod("cleanup");
                cleanup.setAccessible(true);
                cleanup.invoke(worker);
                assertRemoved(id);
            } finally {
                if (Files.isDirectory(diagnostic)) Files.delete(diagnostic);
                worker.destroyForcibly();
            }
        }
    }

    private Path createFixture() throws IOException {
        Path source = release.resolve("SandboxWorker.java");
        Files.writeString(source, "package " + PACKAGE + ";\n" + """
                import java.io.*;
                import java.nio.file.*;
                import java.util.*;
                public class SandboxWorker {
                    static String read(String path) throws IOException {
                        return Files.readString(Path.of(path)).trim();
                    }
                    public static void main(String[] args) throws Exception {
                        String status = read("/proc/self/status");
                        if (!status.matches("(?s).*Uid:\\\\s+65534\\\\s+65534.*")
                                || !status.matches("(?s).*CapEff:\\\\s+0+\\\\s.*")
                                || !status.matches("(?s).*NoNewPrivs:\\\\s+1\\\\s.*")
                                || !status.matches("(?s).*Seccomp:\\\\s+2\\\\s.*")
                                || !read("/sys/fs/cgroup/pids.max").equals("64")
                                || !read("/sys/fs/cgroup/cpu.max").equals("100000 100000")
                                || !read("/sys/fs/cgroup/memory.max").equals("268435456")
                                || !read("/sys/fs/cgroup/memory.swap.max").equals("0")
                                || Collections.list(java.net.NetworkInterface.getNetworkInterfaces())
                                    .stream().anyMatch(n -> !n.getName().equals("lo"))
                                || SandboxWorker.class.getResource("/marker") == null
                                || Files.exists(Path.of("/app/libraries/extra.jar"))
                                || Files.exists(Path.of("/app/libraries/unrelated-application-dependency-0.jar"))
                                || Files.exists(Path.of("/app/data"))
                                || System.getenv("PBH_TEST_WINDOWS_SANDBOX") != null)
                            throw new AssertionError("Unexpected sandbox environment");
                        for (String path : List.of("/app/PeerBanHelper.jar", "/app/libraries/gson-1.jar", "/tmp/probe")) {
                            try (var out = Files.newOutputStream(Path.of(path), StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                                throw new AssertionError("Writable " + path);
                            } catch (IOException expected) { }
                        }
                        var in = new BufferedReader(new InputStreamReader(System.in));
                        String request = in.readLine();
                        System.out.println("reply:" + request);
                        System.out.println(read("/etc/hostname"));
                        if ("hold".equals(request)) Thread.sleep(60000);
                        while (in.readLine() != null) { }
                    }
                }
                """);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "--release", "25", "-d", release.toString(), source.toString()));
        Path libraries = Files.createDirectory(release.resolve("libraries"));
        try (var lib = archive(libraries.resolve("gson-1.jar"), null)) {
            lib.putNextEntry(new JarEntry("marker"));
        }
        Files.createFile(libraries.resolve("extra.jar"));
        Files.createDirectory(release.resolve("data"));
        Path app = release.resolve("PeerBanHelper.jar");
        String unrelated = IntStream.range(0, 400)
                .mapToObj(i -> "libraries/unrelated-application-dependency-" + i + ".jar")
                .collect(Collectors.joining(" "));
        Files.createFile(libraries.resolve("unrelated-application-dependency-0.jar"));
        assertTrue(unrelated.length() * 2 > 32767); // Each individual bind would repeat its source in the target.
        try (var out = archive(app, "libraries/gson-1.jar " + unrelated)) {
            out.putNextEntry(new JarEntry(WORKER));
            Files.copy(release.resolve(WORKER), out);
            var launcher = WindowsSandboxLauncher.class;
            for (var type : List.of(launcher, launcher.getDeclaredClasses()[0], WorkerLibraries.class)) {
                String entry = type.getName().replace('.', '/') + ".class";
                out.putNextEntry(new JarEntry(entry));
                try (var bytes = type.getResourceAsStream("/" + entry)) {
                    bytes.transferTo(out);
                }
            }
        }
        return app;
    }

    private static JarOutputStream archive(Path path, String classPath) throws IOException {
        var manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (classPath != null) manifest.getMainAttributes().put(Attributes.Name.CLASS_PATH, classPath);
        return new JarOutputStream(Files.newOutputStream(path), manifest);
    }

    private static void assertRemoved(String id) throws Exception {
        var inspect = new ProcessBuilder(WindowsSandboxLauncher.dockerExecutable().toString(),
                "--host", "npipe:////./pipe/dockerDesktopLinuxEngine", "container", "inspect", id)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        String error = new String(inspect.getErrorStream().readAllBytes());
        assertNotEquals(0, inspect.waitFor(), "Sandbox container still exists after waitFor");
        assertTrue(error.contains("No such container") || error.contains("No such object"), error);
    }
}
