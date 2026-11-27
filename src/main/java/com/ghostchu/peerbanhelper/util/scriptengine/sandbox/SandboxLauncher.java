package com.ghostchu.peerbanhelper.util.scriptengine.sandbox;

import com.ghostchu.peerbanhelper.Main;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

/** Starts a stdio worker. Classpath artifacts and the installed JVM are trusted inputs. */
public final class SandboxLauncher {
    private static final String WORKER = SandboxLauncher.class.getPackageName() + ".SandboxWorker";
    private static final String ENGINE_PACKAGE = "com/ghostchu/peerbanhelper/util/scriptengine";

    private static final byte[] READY = "PBH-WORKER\n".getBytes(StandardCharsets.US_ASCII);
    private static final int ACK = 6;

    private SandboxLauncher() {}

    public static Process start() throws IOException {
        String os = System.getProperty("os.name");
        if (os.startsWith("Windows")) {
            return WindowsSandboxLauncher.start();
        }
        if (!os.equals("Linux") || !System.getProperty("os.arch").equals("amd64")) {
            throw new IOException("Unsupported worker sandbox platform: " + os + "/" + System.getProperty("os.arch"));
        }
        var data = Main.getDataDirectory();
        return startLinux(System.getProperty("java.class.path"), Path.of("").toRealPath(),
                data == null ? null : data.toPath().toRealPath());
    }

    static Process startLinux(String cp, Path cwd, Path data) throws IOException {
        // RLIMIT_NPROC has a real-UID 0 exemption. Refuse that execution mode.
        if (Files.readAllLines(Path.of("/proc/self/status")).stream()
                .noneMatch(line -> line.matches("Uid:\\s+[1-9]\\d*\\s+[1-9]\\d*\\s+.*"))) {
            throw new IOException("The Linux sandbox requires an unprivileged real and effective UID");
        }
        return launch(classPath(cp, cwd, data));
    }

    static List<Path> classPath(String value, Path cwd, Path data) throws IOException {
        Set<Path> paths = new LinkedHashSet<>();
        for (String entry : value.split(":", -1)) {
            addEntry(paths, cwd.resolve(entry), cwd, data);
        }
        return List.copyOf(paths);
    }

    private static void addEntry(Set<Path> paths, Path source, Path cwd, Path data) throws IOException {
        Path file = source.toRealPath();
        if (cwd.startsWith(file) || (data != null && (file.startsWith(data) || data.startsWith(file)))) {
            throw new IOException("Classpath overlaps a private directory: " + file);
        }
        if (Files.isDirectory(file)) {
            if (file.endsWith("build/resources/main")) return;
            if (!(file.endsWith("build/classes/java/main") || file.endsWith("build/classes/kotlin/main"))) {
                throw new IOException("Unsupported loose classpath directory: " + file);
            }
            Path pkg = file.resolve(ENGINE_PACKAGE);
            if (Files.notExists(pkg)) return;
            if (!pkg.equals(pkg.toRealPath())) throw new IOException("Redirected script class directory: " + pkg);
            paths.add(file);
            return;
        }
        String name = file.getFileName().toString();
        if (name.endsWith(".pom")) return; // Gradle's GraalJS dependency aggregators.
        if (!Files.isRegularFile(file) || !name.endsWith(".jar")) {
            throw new IOException("Expected a classpath JAR: " + file);
        }
        if (!name.equals("PeerBanHelper.jar") && !WorkerLibraries.includes(name)) return;
        if (!paths.add(file)) {
            return;
        }
        try (var jar = new JarFile(file.toFile(), false)) {
            var manifest = jar.getManifest();
            if (manifest == null) return;
            String refs = manifest.getMainAttributes().getValue(Attributes.Name.CLASS_PATH);
            if (refs == null || refs.isBlank()) return;
            for (String ref : refs.trim().split("\\s+")) {
                URI uri = URI.create(ref);
                if (uri.isAbsolute() || uri.getAuthority() != null || uri.getQuery() != null
                        || uri.getFragment() != null || uri.getPath().startsWith("/")) {
                    throw new IOException("Unsupported manifest Class-Path reference: " + ref);
                }
                // Resolve against the declaring JAR, including for nested manifests.
                Path dir = source.toAbsolutePath().getParent().toRealPath();
                Path dep = dir.resolve(uri.getPath()).normalize();
                if (!dep.startsWith(dir) || !(dep.toString().endsWith(".jar") || dep.toString().endsWith(".pom"))) {
                    throw new IOException("Unsupported manifest Class-Path location: " + ref);
                }
                if (Files.notExists(dep)) continue; // JVM ignores missing optional entries (e.g. swt.jar).
                if (!dep.toRealPath().startsWith(dir)) throw new IOException("External JAR symlink: " + dep);
                addEntry(paths, dep, cwd, data);
            }
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid manifest Class-Path in " + file, e);
        }
    }

    private static Process launch(List<Path> paths) throws IOException {
        Path filter = Files.createTempFile("pbh-seccomp-", ".bpf");
        Process child = null;
        try {
            Files.write(filter, seccomp());
            // ProcessBuilder has no pass-fd API. Only a fixed shell fragment runs on the host.
            // exec preserves the parent relationship with bwrap.
            var cmd = new ArrayList<>(List.of("/bin/sh", "-c", "exec 3<\"$1\"; shift; exec \"$@\"",
                    "pbh-worker", filter.toString(), "/usr/bin/bwrap", "--unshare-user", "--unshare-net", "--unshare-pid",
                    "--unshare-ipc", "--unshare-uts", "--disable-userns", "--cap-drop", "ALL",
                    "--die-with-parent", "--as-pid-1", "--new-session", "--clearenv", "--setenv", "LD_LIBRARY_PATH",
                    "/jre/lib", "--dir", "/tmp", "--chdir", "/tmp", "--seccomp", "3"));
            Path jre = Path.of(System.getProperty("java.home"));
            for (String part : List.of("bin/java", "lib/modules", "lib/server/libjvm.so", "lib/jvm.cfg",
                    "lib/libjli.so", "lib/libjava.so", "lib/libjimage.so", "lib/libzip.so", "lib/libnio.so",
                    "lib/libnet.so", "lib/libverify.so", "lib/tzdb.dat", "conf/security/java.security")) {
                bind(cmd, jre.resolve(part), "/jre/" + part);
            }
            Path loader = Path.of("/lib64/ld-linux-x86-64.so.2");
            bind(cmd, loader, loader.toString());
            Path libs = Path.of("/lib/x86_64-linux-gnu");
            for (String name : List.of("libc.so.6", "libm.so.6", "libdl.so.2", "librt.so.1",
                    "libpthread.so.0", "libz.so.1", "libgcc_s.so.1", "libstdc++.so.6", "libsmartcols.so.1")) {
                bind(cmd, libs.resolve(name), libs.resolve(name).toString());
            }
            for (String device : List.of("/dev/null", "/dev/urandom", "/dev/random")) {
                cmd.addAll(List.of("--dev-bind", device, device));
            }
            var inside = new ArrayList<String>();
            for (Path path : paths) {
                // Distinct parents prevent a manifest URL from accidentally finding a sibling.
                String dest = "/cp/" + inside.size() + (Files.isDirectory(path) ? "/classes" : "/entry.jar");
                if (Files.isDirectory(path)) {
                    // Loose Gradle output may have unrelated resources next to its classes.
                    // Bind individual class files without traversing symlinked directories.
                    try (var files = Files.walk(path.resolve(ENGINE_PACKAGE))) {
                        for (Path compiled : files.filter(f -> f.toString().endsWith(".class")
                                && Files.isRegularFile(f, LinkOption.NOFOLLOW_LINKS)).sorted().toList()) {
                            bind(cmd, compiled, dest + "/" + path.relativize(compiled));
                        }
                    }
                } else {
                    bind(cmd, path, dest);
                }
                inside.add(dest);
            }
            bind(cmd, Path.of("/usr/bin/prlimit"), "/bin/prlimit");
            cmd.addAll(List.of("--remount-ro", "/", "--", "/bin/prlimit", "--as=1073741824:1073741824",
                    "--cpu=30:30", "--nproc=64:64", "--nofile=256:256", "--core=0:0", "--fsize=0:0",
                    "--", "/jre/bin/java", "-Xshare:off", "-Xms8m",
                    "-Xmx64m", "-Xss512k", "-XX:+UseSerialGC", "-XX:ActiveProcessorCount=1",
                    "-XX:MaxMetaspaceSize=64m", "-XX:CompressedClassSpaceSize=32m",
                    "-XX:ReservedCodeCacheSize=32m", "-XX:MaxDirectMemorySize=16m",
                    "-XX:-UsePerfData", "-XX:+DisableAttachMechanism", "-XX:-CreateCoredumpOnCrash",
                    "-cp", String.join(":", inside), Bootstrap.class.getName()));
            var builder = new ProcessBuilder(cmd).directory(new File("/"));
            builder.environment().clear();
            child = builder.start();
            var process = new WorkerProcess(child);
            process.awaitBootstrap();
            Files.delete(filter);
            return process;
        } catch (Throwable failure) {
            if (child != null) child.destroyForcibly();
            throw failure;
        } finally {
            Files.deleteIfExists(filter);
        }
    }

    private static void bind(List<String> cmd, Path source, String dest) throws IOException {
        cmd.addAll(List.of("--ro-bind", source.toRealPath().toString(), dest));
    }

    // Classic BPF for Linux x86-64 only. An allowlist also excludes x32, sockets, io_uring,
    // namespace creation, ptrace, SysV IPC and alternate process creation mechanisms.
    private static byte[] seccomp() {
        var bpf = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN);
        int allow = 0x7fff0000;
        int enosys = 0x00050026; // glibc falls back from clone3 to inspectable clone flags
        insn(bpf, 0x20, 0, 0, 4); // seccomp_data.arch
        insn(bpf, 0x15, 1, 0, 0xc000003e); // AUDIT_ARCH_X86_64
        insn(bpf, 0x06, 0, 0, 0x80000000); // KILL_PROCESS
        insn(bpf, 0x20, 0, 0, 0); // seccomp_data.nr
        insn(bpf, 0x15, 0, 4, 56); // clone
        insn(bpf, 0x20, 0, 0, 16); // args[0], low word of flags
        insn(bpf, 0x45, 0, 1, 0x10000); // CLONE_THREAD
        insn(bpf, 0x06, 0, 0, allow);
        insn(bpf, 0x06, 0, 0, enosys);
        int[] allowed = {
                0, 1, 2, 3, 4, 5, 6, 7, 8, // read/write, open/close, stat family, poll, lseek
                9, 10, 11, 12, 13, 14, 15, // mmap/mprotect/munmap, brk, rt_sig*
                16, 17, 19, 20, 21, 24, 25, 28, // ioctl, pread, readv/writev, access, scheduling, VM
                35, 39, 59, 60, 63, 72, 78, 79, 89, 96, 97, 98, 99, 100,
                // nanosleep, getpid, initial execve, exit, uname, fcntl, getdents, getcwd,
                // readlink, gettimeofday, getrlimit, getrusage, sysinfo, times
                102, 104, 107, 108, 110, 111, 124, 131, 158, 186,  // identity, getsid, sigaltstack, arch_prctl, gettid
                202, 204, 217, 218, 228, 229, 230, 231, 234, // futex, affinity, getdents64, tid, clocks, exit_group, tgkill
                257, 262, 267, 269, 270, 271, 273, 302, 309, 318, 332, 334, 439
                // openat, newfstatat, readlinkat, faccessat, pselect6, ppoll, robust list,
                // prlimit64, getcpu, getrandom, statx, rseq, faccessat2
        };
        for (int syscall : allowed) {
            insn(bpf, 0x15, 0, 1, syscall);
            insn(bpf, 0x06, 0, 0, allow);
        }
        insn(bpf, 0x06, 0, 0, enosys);
        return Arrays.copyOf(bpf.array(), bpf.position());
    }

    private static void insn(ByteBuffer bpf, int opcode, int yes, int no, int operand) {
        bpf.putShort((short) opcode).put((byte) yes).put((byte) no).putInt(operand);
    }

    /** Runs inside the sandbox. Its private preamble is consumed before handing stdio to the caller. */
    public static final class Bootstrap {
        public static void main(String[] args) throws ReflectiveOperationException, IOException {
            var main = Class.forName(WORKER, false, Bootstrap.class.getClassLoader()).getMethod("main", String[].class);
            if (!java.lang.reflect.Modifier.isStatic(main.getModifiers()) || main.getReturnType() != void.class) {
                throw new IOException("Invalid SandboxWorker.main");
            }
            System.out.write(READY);
            System.out.flush();
            // Cover a parent death before bwrap had time to arm PR_SET_PDEATHSIG.
            // An orphaned bootstrap sees EOF and never enters the worker.
            if (System.in.read() != ACK) throw new IOException("Sandbox parent disappeared");
            main.invoke(null, (Object) args);
        }
    }

    private static final class WorkerProcess extends Process {
        private final Process child;
        private final OutputBuffer stdout = new OutputBuffer();
        private final OutputBuffer stderr = new OutputBuffer();

        WorkerProcess(Process child) {
            this.child = child;
            stderr.drain(child.getErrorStream(), child);
        }

        void awaitBootstrap() throws IOException {
            var input = child.getInputStream();
            var ready = new FutureTask<>(() -> Arrays.equals(READY, input.readNBytes(READY.length)));
            Thread.startVirtualThread(ready);
            try {
                if (!ready.get(10, TimeUnit.SECONDS)) {
                    throw new IOException("Sandbox bootstrap failed: " + stderr.diagnostic());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted starting sandbox", e);
            } catch (ExecutionException | TimeoutException e) {
                throw new IOException("Sandbox startup failed", e);
            }
            stdout.drain(input, child);
            child.getOutputStream().write(ACK);
            child.getOutputStream().flush();
        }

        @Override public OutputStream getOutputStream() { return child.getOutputStream(); }
        @Override public InputStream getInputStream() { return stdout; }
        @Override public InputStream getErrorStream() { return stderr; }
        @Override public int waitFor() throws InterruptedException { return child.waitFor(); }
        @Override public boolean waitFor(long time, TimeUnit unit) throws InterruptedException { return child.waitFor(time, unit); }
        @Override public int exitValue() { return child.exitValue(); }
        @Override public boolean isAlive() { return child.isAlive(); }
        @Override public long pid() { return child.pid(); }
        @Override public ProcessHandle toHandle() { return child.toHandle(); }
        @Override public CompletableFuture<Process> onExit() { return child.onExit().thenApply(p -> this); }
        @Override public void destroy() { child.destroyForcibly(); }
        @Override public Process destroyForcibly() { child.destroyForcibly(); return this; }
    }

    /** Lifetime quota per stream. Drain independently even if the protocol consumer stops reading. */
    private static final class OutputBuffer extends InputStream {
        private final byte[] bytes = new byte[512 * 1024];
        private int read;
        private int end;
        private boolean done;

        void drain(InputStream source, Process child) {
            Thread.startVirtualThread(() -> {
                try (source) {
                    int n;
                    while (end < bytes.length && (n = source.read(bytes, end, bytes.length - end)) != -1) {
                        synchronized (this) {
                            end += n;
                            notifyAll();
                        }
                    }
                    if (end == bytes.length && source.read() != -1) child.destroyForcibly();
                } catch (IOException e) {
                    child.destroyForcibly();
                } finally {
                    synchronized (this) {
                        done = true;
                        notifyAll();
                    }
                }
            });
        }

        synchronized String diagnostic() {
            return new String(bytes, 0, Math.min(end, 2048), StandardCharsets.UTF_8).strip();
        }

        private boolean awaitData() throws IOException {
            try {
                while (read == end && !done) wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException();
            }
            return read < end;
        }

        @Override public synchronized int read() throws IOException {
            return awaitData() ? Byte.toUnsignedInt(bytes[read++]) : -1;
        }

        @Override public synchronized int read(byte[] target, int off, int len) throws IOException {
            java.util.Objects.checkFromIndexSize(off, len, target.length);
            if (len == 0) return 0;
            if (!awaitData()) return -1;
            int count = Math.min(len, end - read);
            System.arraycopy(bytes, read, target, off, count);
            read += count;
            return count;
        }
    }
}
