package com.ghostchu.peerbanhelper.util.scriptengine.sandbox;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

/** Runs the worker through the local Docker Desktop Linux engine. */
public final class WindowsSandboxLauncher {
    static final String IMAGE = "eclipse-temurin@sha256:b8705e936964c228dfce359e63dbf08f6c69cb380ed1d576b1f6b1064f15567d";
    private static final String WORKER = WindowsSandboxLauncher.class.getPackageName() + ".SandboxWorker";
    private static final int CSIDL_LOCAL_APPDATA = 0x1c;
    private static final int CSIDL_WINDOWS = 0x24;
    private static final int CSIDL_PROGRAM_FILES = 0x26;

    private static final System.Logger LOG = System.getLogger(WindowsSandboxLauncher.class.getName());

    private WindowsSandboxLauncher() {
    }

    public static Process start() throws IOException {
        if (File.separatorChar != '\\') {
            throw new IOException("The Windows sandbox requires Docker Desktop for Windows");
        }
        var source = WindowsSandboxLauncher.class.getProtectionDomain().getCodeSource();
        if (source == null || !"file".equals(source.getLocation().getProtocol())) {
            throw new IOException("The Windows sandbox requires an installed PeerBanHelper.jar");
        }
        Path jar;
        try {
            jar = Path.of(source.getLocation().toURI());
        } catch (URISyntaxException | IllegalArgumentException e) {
            throw new IOException("Invalid PeerBanHelper.jar location", e);
        }
        List<String> options = createOptions(jar);
        Path docker = dockerExecutable();
        Path windows = shellFolder(CSIDL_WINDOWS);
        Path config = Files.createTempDirectory(shellFolder(CSIDL_LOCAL_APPDATA), "pbh-docker-");
        var container = new Container(docker, jar.getParent(), config, windows);
        return container.start(options);
    }

    static Path dockerExecutable() throws IOException {
        // Standard machine and per-user installations only. Never search PATH or the current directory.
        for (Path root : List.of(shellFolder(CSIDL_PROGRAM_FILES).resolve("Docker/Docker"),
                shellFolder(CSIDL_LOCAL_APPDATA).resolve("Programs/DockerDesktop"))) {
            Path exe = root.resolve("resources/bin/docker.exe");
            if (exe.isAbsolute() && Files.isRegularFile(exe) && exe.equals(exe.toRealPath())) {
                return exe;
            }
        }
        throw new IOException("Docker Desktop CLI was not found in a supported Windows installation directory");
    }

    // Ask the Windows shell for installation/profile paths without consulting PATH, user.home or TEMP.
    private static Path shellFolder(int csidl) throws IOException {
        try (var arena = Arena.ofConfined()) {
            var shell = SymbolLookup.libraryLookup("shell32", arena);
            var getFolder = Linker.nativeLinker().downcallHandle(shell.findOrThrow("SHGetFolderPathW"),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            var buffer = arena.allocate(260L * Character.BYTES, Character.BYTES);
            int hr = (int) getFolder.invokeExact(MemorySegment.NULL, csidl, MemorySegment.NULL, 0, buffer);
            if (hr != 0) throw new IOException("Windows shell folder lookup failed: " + hr);
            Path path = Path.of(buffer.getString(0, StandardCharsets.UTF_16LE));
            if (!path.isAbsolute() || !path.equals(path.toRealPath())) {
                throw new IOException("Unsupported redirected Windows shell folder");
            }
            return path;
        } catch (IOException e) {
            throw e;
        } catch (Throwable e) {
            throw new IOException("Could not resolve the Windows shell folder", e);
        }
    }

    static List<String> createOptions(Path jar) throws IOException {
        if (!jar.isAbsolute() || !"PeerBanHelper.jar".equals(jar.getFileName().toString())) {
            throw new IOException("The Windows sandbox requires the release layout PeerBanHelper.jar and libraries/");
        }
        List<String> args = new ArrayList<>(List.of("--pull=never", "--rm", "--interactive",
                "--restart=no", "--network=none", "--ipc=none", "--cgroupns=private", "--read-only",
                "--user=65534:65534", "--cap-drop=ALL", "--security-opt=no-new-privileges",
                "--pids-limit=64", "--cpus=1", "--memory=256m", "--memory-swap=256m",
                "--ulimit=nofile=256:256", "--ulimit=core=0:0", "--log-driver=none",
                "--workdir=/app", "--entrypoint=/usr/bin/timeout"));
        Path release = jar.getParent();
        addMount(args, release, "PeerBanHelper.jar");
        try (JarFile archive = new JarFile(jar.toFile())) {
            if (archive.getJarEntry(WORKER.replace('.', '/') + ".class") == null) {
                throw new IOException("SandboxWorker is missing from PeerBanHelper.jar");
            }
            var manifest = archive.getManifest();
            String classPath = manifest == null ? null : manifest.getMainAttributes().getValue(Attributes.Name.CLASS_PATH);
            if (classPath == null || classPath.isBlank()) {
                throw new IOException("Release manifest is missing its library class path");
            }
            for (String entry : classPath.trim().split(" +")) {
                // Gradle also lists POM-only GraalVM dependency aggregators in Class-Path.
                if (!entry.matches("libraries/[A-Za-z0-9_+-][A-Za-z0-9._+-]*\\.(?:jar|pom)")) {
                    throw new IOException("Invalid library entry in the release manifest");
                }
                if (WorkerLibraries.includes(entry.substring("libraries/".length()))) {
                    addMount(args, release, entry);
                }
            }
        }
        // The in-container deadline also applies if Windows or the host JVM terminates abruptly.
        args.addAll(List.of(IMAGE, "--signal=KILL", "1h", "/opt/java/openjdk/bin/java",
                "-XX:-UsePerfData", "-XX:+DisableAttachMechanism", "-XX:ActiveProcessorCount=1",
                "-Xmx128m", "-Djava.awt.headless=true", "-cp", "/app/PeerBanHelper.jar", WORKER));
        return args;
    }

    private static void addMount(List<String> args, Path release, String entry) throws IOException {
        Path file = release.resolve(entry);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || !file.equals(file.toRealPath())) {
            throw new IOException("Missing or redirected release file: " + entry);
        }
        // The CLI resolves these relative sources against its explicitly set release working directory.
        // Keeping arguments relative also avoids the Windows command line length limit on deep installations.
        args.add("--mount");
        args.add("type=bind,src=./" + entry + ",dst=/app/" + entry + ",readonly");
    }

    private static final class Container extends Process {
        private final Path docker;
        private final Path release;
        private final Path config;
        private final Path windows;
        private final String name = "pbh-worker-" + UUID.randomUUID();
        private final Thread shutdown = Thread.ofPlatform().unstarted(() -> {
            try {
                cleanup();
            } catch (UncheckedIOException e) {
                LOG.log(System.Logger.Level.ERROR, "Could not confirm removal of " + name, e);
            }
        });
        private Process attached;
        private CompletableFuture<Void> removed;
        private boolean cleaned;

        Container(Path docker, Path release, Path config, Path windows) {
            this.docker = docker;
            this.release = release;
            this.config = config;
            this.windows = windows;
        }

        synchronized Process start(List<String> options) throws IOException {
            try {
                Runtime.getRuntime().addShutdownHook(shutdown);
                List<String> create = new ArrayList<>(List.of("create", "--name", name));
                create.addAll(options);
                control(create);
                // Creation is complete before exposing a killable stdio client to the caller.
                attached = cli(List.of("start", "--attach", "--interactive", name)).start();
                removed = attached.onExit().thenRunAsync(this::cleanup, Thread::startVirtualThread);
                return this;
            } catch (IOException | RuntimeException failure) {
                try {
                    cleanup();
                } catch (UncheckedIOException unconfirmed) {
                    failure.addSuppressed(unconfirmed);
                    // 'create' alone never runs the worker. Avoid accumulating hooks on a stopped engine.
                    if (attached == null) discardConfig();
                }
                throw failure;
            }
        }

        // The pool may reuse a process slot after onExit/waitFor. Keep the slot until the Docker
        // removal command has completed, including when the stdio client itself was killed.
        @Override public InputStream getInputStream() { return attached.getInputStream(); }
        @Override public InputStream getErrorStream() { return attached.getErrorStream(); }
        @Override public OutputStream getOutputStream() { return attached.getOutputStream(); }
        @Override public long pid() { return attached.pid(); }
        @Override public ProcessHandle toHandle() { return attached.toHandle(); }
        @Override public void destroy() { destroyForcibly(); }
        @Override public Process destroyForcibly() { attached.toHandle().destroyForcibly(); return this; }
        @Override public boolean isAlive() { return !removed.isDone() || removed.isCompletedExceptionally(); }
        @Override public CompletableFuture<Process> onExit() { return removed.thenApply(ignored -> this); }

        @Override
        public int exitValue() {
            if (isAlive()) throw new IllegalThreadStateException("Docker worker is still terminating");
            return attached.exitValue();
        }

        @Override
        public int waitFor() throws InterruptedException {
            try {
                removed.get();
                return attached.exitValue();
            } catch (ExecutionException e) {
                throw new IllegalStateException("Docker worker cleanup failed", e.getCause());
            }
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
            try {
                removed.get(timeout, unit);
                return true;
            } catch (TimeoutException e) {
                return false;
            } catch (ExecutionException e) {
                throw new IllegalStateException("Docker worker cleanup failed", e.getCause());
            }
        }

        private ProcessBuilder cli(List<String> args) throws IOException {
            List<String> cmd = new ArrayList<>(List.of(docker.toString(), "--config", config.toString(),
                    "--host", "npipe:////./pipe/dockerDesktopLinuxEngine"));
            cmd.addAll(args);
            // Upper bound including two quotes and a separator per argument (including argv[0]),
            // and the terminating NUL. No arguments end in backslashes or contain double quotes.
            long units = 1;
            for (String arg : cmd) units += arg.length() + 3L;
            if (units > 32767) {
                throw new IOException("Docker Desktop command exceeds the Windows process argument limit");
            }
            var builder = new ProcessBuilder(cmd).directory(release.toFile());
            var env = builder.environment();
            env.clear();
            env.put("SystemRoot", windows.toString());
            env.put("USERPROFILE", config.toString());
            env.put("LOCALAPPDATA", config.toString());
            env.put("APPDATA", config.toString());
            return builder;
        }

        private String control(List<String> args) throws IOException {
            Path error = config.resolve("stderr");
            Path output = config.resolve("stdout");
            Process process = cli(args).redirectOutput(output.toFile())
                    .redirectError(error.toFile()).start();
            try {
                if (!process.waitFor(20, TimeUnit.SECONDS)) {
                    throw new IOException("Docker Desktop timed out during " + args.getFirst());
                }
                if (process.exitValue() != 0) {
                    String message = Files.readString(error).strip();
                    throw new IOException("Docker Desktop " + args.getFirst() + " failed: " + message);
                }
                return Files.readString(output).strip();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted during Docker Desktop " + args.getFirst(), e);
            } finally {
                process.destroyForcibly();
            }
        }

        private void removeAndConfirm() throws IOException {
            IOException removalFailure = null;
            try {
                control(List.of("rm", "--force", name));
            } catch (IOException e) {
                removalFailure = e; // --rm may already have removed it. Ask the daemon directly.
            }
            try {
                String ids = control(List.of("ps", "--all", "--quiet", "--filter", "name=^/" + name + "$"));
                if (!ids.isEmpty()) throw new IOException("Docker still lists sandbox container " + name);
            } catch (IOException e) {
                if (removalFailure != null) e.addSuppressed(removalFailure);
                throw e;
            }
        }

        private synchronized void cleanup() {
            if (cleaned) {
                return;
            }
            boolean interrupted = Thread.interrupted();
            try {
                for (int attempt = 0; ; attempt++) {
                    try {
                        removeAndConfirm();
                        break;
                    } catch (IOException e) {
                        if (attempt == 2) throw new UncheckedIOException("Unconfirmed removal of " + name, e);
                        Thread.sleep(250L << attempt);
                    }
                }
                cleaned = true;
                discardConfig();
            } catch (InterruptedException e) {
                interrupted = true;
                throw new UncheckedIOException(new IOException("Interrupted while removing " + name, e));
            } finally {
                if (interrupted) Thread.currentThread().interrupt();
            }
        }

        private void discardConfig() {
            try {
                Files.deleteIfExists(config.resolve("stderr"));
                Files.deleteIfExists(config.resolve("stdout"));
                Files.deleteIfExists(config);
            } catch (IOException e) {
                LOG.log(System.Logger.Level.WARNING, "Could not remove temporary Docker CLI configuration", e);
            }
            try {
                Runtime.getRuntime().removeShutdownHook(shutdown);
            } catch (IllegalStateException ignored) {
                // JVM shutdown is in progress.
            }
        }
    }
}
