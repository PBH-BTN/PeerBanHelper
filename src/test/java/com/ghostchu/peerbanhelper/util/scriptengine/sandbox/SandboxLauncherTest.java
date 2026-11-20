package com.ghostchu.peerbanhelper.util.scriptengine.sandbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@EnabledOnOs(OS.LINUX)
@Timeout(90)
class SandboxLauncherTest {
    @TempDir Path temp;

    @Test
    void releaseReferencesAreRelativeOrderedAndRestricted() throws Exception {
        Path lib = Files.createDirectories(temp.resolve("release with spaces/libraries"));
        Path dep = jar(lib.resolve("gson-1.jar"), null);
        Path parent = jar(lib.resolve("aviator-1.jar"), "gson-1.jar");
        jar(lib.resolve("unrelated-1.jar"), null);
        Path app = jar(lib.getParent().resolve("PeerBanHelper.jar"),
                "libraries/aviator-1.jar libraries/js-1.pom libraries/unrelated-1.jar libraries/swt.jar");
        assertEquals(List.of(app, parent, dep), SandboxLauncher.classPath(temp.relativize(app).toString(), temp, null));
        assertThrows(IOException.class, () -> SandboxLauncher.classPath(".", temp, null));
        assertThrows(IOException.class, () -> SandboxLauncher.classPath(app.toString(), temp, lib));
        jar(app, "../outside.jar");
        assertThrows(IOException.class, () -> SandboxLauncher.classPath(app.toString(), temp, null));
        jar(app, "file:///etc/passwd");
        assertThrows(IOException.class, () -> SandboxLauncher.classPath(app.toString(), temp, null));
    }

    private Path jar(Path file, String refs) throws IOException {
        var manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (refs != null) manifest.getMainAttributes().put(Attributes.Name.CLASS_PATH, refs);
        try (var out = new JarOutputStream(Files.newOutputStream(file), manifest)) {
            out.putNextEntry(new JarEntry("marker"));
        }
        return file;
    }

    private static void requireLinuxTools() throws IOException {
        assumeTrue(System.getProperty("os.arch").equals("amd64") && Files.isExecutable(Path.of("/usr/bin/bwrap"))
                && Files.isExecutable(Path.of("/usr/bin/prlimit")) && Files.exists(Path.of("/lib/x86_64-linux-gnu")));
        assumeTrue(Files.readString(Path.of("/proc/self/status")).lines()
                .anyMatch(line -> line.matches("Uid:\\s+[1-9]\\d*\\s+[1-9]\\d*\\s+.*")));
    }

    private Path compileProbe() throws IOException {
        Path classes = Files.createDirectories(temp.resolve("build/classes/java/main"));
        Path source = temp.resolve("SandboxWorker.java");
        Files.writeString(source, """
                package com.ghostchu.peerbanhelper.util.scriptengine.sandbox;
                import java.nio.file.*;
                public class SandboxWorker {
                    public static void main(String[] args) throws Exception {
                        switch (System.in.read()) {
                            case 'p' -> {
                                String secret = new String(System.in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                                if (Files.exists(Path.of(secret)) || Files.exists(Path.of("/proc/self"))
                                        || Files.exists(Path.of("/etc/passwd")) || System.getenv("HOME") != null
                                        || SandboxWorker.class.getResource("neighbor.secret") != null)
                                    throw new AssertionError("host exposure");
                                try { Files.createFile(Path.of("/tmp/write")); throw new AssertionError("writable root"); }
                                catch (java.io.IOException expected) {}
                                try (var socket = new java.net.ServerSocket(0)) { throw new AssertionError("socket"); }
                                catch (java.io.IOException expected) {}
                                catch (ExceptionInInitializerError e) {
                                    if (!(e.getCause() instanceof java.io.IOException)) throw e;
                                }
                                System.setProperty("jdk.lang.Process.launchMechanism", "FORK");
                                try { new ProcessBuilder("/jre/bin/java", "-version").start(); throw new AssertionError("fork"); }
                                catch (java.io.IOException expected) {}
                                Thread.ofPlatform().start(() -> System.out.print("thread")).join();
                                System.out.print(":" + Runtime.getRuntime().maxMemory());
                            }
                            case 't' -> {
                                var hold = new java.util.concurrent.CountDownLatch(1);
                                try {
                                    for (int i = 0; i < 65; i++) Thread.ofPlatform().daemon().start(() -> {
                                        try { hold.await(); } catch (InterruptedException e) { throw new AssertionError(e); }
                                    });
                                    throw new AssertionError("thread limit missing");
                                } catch (OutOfMemoryError limit) {
                                    System.err.print("limited");
                                } finally {
                                    hold.countDown();
                                }
                            }
                            case 'o' -> {
                                var out = System.in.read() == 1 ? System.err : System.out;
                                byte[] block = new byte[8192];
                                while (true) out.write(block);
                            }
                            default -> Thread.sleep(60000);
                        }
                    }
                }
                """);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", classes.toString(), source.toString()));
        var launcherClasses = new LinkedHashSet<>(Arrays.asList(SandboxLauncher.class.getDeclaredClasses()));
        launcherClasses.add(SandboxLauncher.class);
        launcherClasses.add(WorkerLibraries.class);
        for (Class<?> c : launcherClasses) {
            String resource = c.getName().replace('.', '/') + ".class";
            try (var input = c.getClassLoader().getResourceAsStream(resource)) {
                Files.copy(java.util.Objects.requireNonNull(input), classes.resolve(resource));
            }
        }
        Files.writeString(classes.resolve(SandboxLauncher.class.getPackageName().replace('.', '/') + "/neighbor.secret"), "private");
        return classes;
    }

    public static final class Parent {
        public static void main(String[] args) throws Exception {
            Process worker = SandboxLauncher.startLinux(args[0], Path.of("").toRealPath(), null);
            System.out.println(worker.pid());
            Thread.sleep(60000);
        }
    }

    @Test
    void killingParentKillsSessionAndWorker() throws Exception {
        requireLinuxTools();
        Path probe = compileProbe();
        String testClasses = Path.of(Parent.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        var parent = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java").toString(), "-cp",
                applicationClasspath() + ":" + testClasses, Parent.class.getName(), probe.toString())
                .redirectError(ProcessBuilder.Redirect.INHERIT).start();
        List<ProcessHandle> children = List.of();
        try {
            long supervisor = Long.parseLong(parent.inputReader().readLine());
            children = parent.descendants().toList();
            assertTrue(children.stream().anyMatch(p -> p.pid() == supervisor));
            assertTrue(children.size() >= 2, "Expected both bwrap and the worker JVM");
            var worker = children.stream().filter(p -> p.pid() != supervisor).findFirst().orElseThrow();
            String limits = Files.readString(Path.of("/proc", Long.toString(worker.pid()), "limits"));
            for (String limit : List.of("Max cpu time\\s+30\\s+30", "Max processes\\s+64\\s+64",
                    "Max address space\\s+1073741824\\s+1073741824")) {
                assertTrue(java.util.regex.Pattern.compile(limit).matcher(limits).find(), limits);
            }
            parent.destroyForcibly().waitFor();
            for (ProcessHandle child : children) child.onExit().get(10, TimeUnit.SECONDS);
        } finally {
            parent.destroyForcibly();
            children.forEach(ProcessHandle::destroyForcibly);
        }
    }

    @Test
    void realNamespacesSeccompAndOutputLimits() throws Exception {
        requireLinuxTools();
        Path dir = compileProbe();
        Path secret = Files.writeString(temp.resolve("host.secret"), "private");
        Process probe = SandboxLauncher.startLinux(dir.toString(), temp, null);
        try {
            probe.getOutputStream().write(('p' + secret.toString()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            probe.getOutputStream().close();
            assertTrue(probe.waitFor(10, TimeUnit.SECONDS));
            assertEquals(0, probe.exitValue(), () -> diagnostic(probe));
            String result = new String(probe.getInputStream().readAllBytes());
            assertTrue(result.startsWith("thread:"), result);
            assertTrue(Long.parseLong(result.substring(7)) <= 64L << 20);
        } finally {
            probe.destroyForcibly();
        }
        Process threads = SandboxLauncher.startLinux(dir.toString(), temp, null);
        try {
            threads.getOutputStream().write('t');
            threads.getOutputStream().flush();
            assertTrue(threads.waitFor(10, TimeUnit.SECONDS));
            assertEquals(0, threads.exitValue(), () -> diagnostic(threads));
            assertTrue(diagnostic(threads).contains("limited"));
        } finally {
            threads.destroyForcibly();
        }
        for (int stream : List.of(0, 1)) {
            Process flood = SandboxLauncher.startLinux(dir.toString(), temp, null);
            try {
                flood.getOutputStream().write(new byte[]{'o', (byte) stream});
                flood.getOutputStream().flush();
                assertTrue(flood.waitFor(10, TimeUnit.SECONDS));
                assertNotEquals(0, flood.exitValue());
                var output = stream == 0 ? flood.getInputStream() : flood.getErrorStream();
                assertEquals(512 * 1024, output.readAllBytes().length);
            } finally {
                flood.destroyForcibly();
            }
        }
        Files.delete(dir.resolve(SandboxLauncher.class.getPackageName().replace('.', '/') + "/SandboxWorker.class"));
        assertThrows(IOException.class, () -> SandboxLauncher.startLinux(dir.toString(), temp, null));
    }

    private static String diagnostic(Process p) {
        try {
            return new String(p.getErrorStream().readAllBytes());
        } catch (IOException e) {
            return e.toString();
        }
    }

    // Gradle's test worker loads its runtime classpath through a URLClassLoader.
    private static String applicationClasspath() throws Exception {
        var paths = new LinkedHashSet<String>();
        for (String path : System.getProperty("java.class.path").split(":")) paths.add(path);
        for (var loader = SandboxLauncher.class.getClassLoader(); loader != null; loader = loader.getParent()) {
            if (loader instanceof URLClassLoader urls) {
                for (var url : urls.getURLs()) paths.add(Path.of(url.toURI()).toString());
            }
        }
        paths.removeIf(p -> !(p.endsWith(".jar") || p.endsWith("build/classes/java/main")
                || p.endsWith("build/classes/kotlin/main")));
        return String.join(":", paths);
    }

    @Test
    void workerRunsThreeEnginesAndAllShippedRules() throws Exception {
        requireLinuxTools();
        String cp = applicationClasspath();
        var normal = peer("qBittorrent", "-qB123", "2001:db8::2");
        for (String ext : List.of(".av", ".js", ".lua")) {
            String expr = ext.equals(".js") ? "peer.clientName" : "return peer.clientName" + (ext.equals(".av") ? ";" : "");
            checkRule(cp, ext, "probe" + ext, expr, List.of(normal), List.of("qBittorrent"));
        }
        var matches = Map.of(
                "2e0-61ff-fe.av", peer("other", "id", "2001:db8:2e0:61ff:fe00::2"),
                "dot-1-ipv6-tr296.av", peer("Transmission 2.94", "-tr123", "2001:db8::1"),
                "gopeed-random-peerid.av", peer("Gopeed Dev", "random", "192.0.2.1"),
                "name-id-verify.av", peer("qBittorrent", "random", "192.0.2.2"));
        for (var entry : matches.entrySet()) {
            String file = entry.getKey();
            try (var in = getClass().getResourceAsStream("/scripts/" + file)) {
                assertNotNull(in, file);
                checkRule(cp, ".av", file, new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8),
                        List.of(normal, entry.getValue()), List.of(false, String.class));
            }
        }
    }

    private static Map<String, Object> peer(String client, String id, String ip) {
        return Map.of("peer", Map.of("clientName", client, "peerId", id, "peerAddress", Map.of("address", ip)));
    }

    private void checkRule(String cp, String extension, String name, String source,
                           List<Map<String, Object>> samples, List<?> expected) throws Exception {
        Process p = SandboxLauncher.startLinux(cp, Path.of("").toRealPath(), null);
        try {
            SandboxProtocol.write(p.getOutputStream(), new SandboxProtocol.Init(extension, name, source, "local"));
            assertNotNull(SandboxProtocol.read(p.getInputStream(), SandboxProtocol.Metadata.class).name(), name);
            for (int i = 0; i < samples.size(); i++) {
                SandboxProtocol.write(p.getOutputStream(), samples.get(i));
                var reply = SandboxProtocol.read(p.getInputStream(), SandboxProtocol.Reply.class);
                assertNull(reply.error(), name);
                if (expected.get(i) == String.class) assertInstanceOf(String.class, reply.result(), name);
                else assertEquals(expected.get(i), reply.result(), name);
            }
            p.getOutputStream().close();
            assertTrue(p.waitFor(10, TimeUnit.SECONDS));
            assertEquals(0, p.exitValue(), () -> diagnostic(p));
        } catch (IOException e) {
            p.destroyForcibly().waitFor();
            throw new AssertionError(name + ": " + diagnostic(p), e);
        } finally {
            p.destroyForcibly();
        }
    }
}
