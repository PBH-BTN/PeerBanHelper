package com.ghostchu.peerbanhelper.util.update;

import com.ghostchu.peerbanhelper.Main;
import com.install4j.api.ApplicationRegistry;
import com.install4j.api.launcher.ApplicationLauncher;
import com.install4j.api.update.UpdateChecker;
import com.install4j.api.update.UpdateDescriptorEntry;
import com.install4j.api.update.UpdateCheckRequest;
import com.install4j.api.update.ApplicationDisplayMode;
import okhttp3.OkHttpClient;
import okhttp3.Request;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

final class Install4jUpdates {
    private static final String PRIMARY = "https://pbh-files.pbh-btn.com/updates/latest/";
    private static final String DEFAULT_MIRROR = "https://github.com/PBH-BTN/PeerBanHelper/releases/latest/download/";
    private static final Set<String> SUPPORTED_MEDIA = Set.of("38", "109", "141", "112", "344", "103", "127", "98", "120", "335");
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(15)).readTimeout(Duration.ofSeconds(30)).build();

    record Target(String version, String media, String name, String comment, long size, String sha256, List<String> sources) {}

    private Install4jUpdates() {}

    static boolean isSupported() {
        try {
            var installation = installation();
            return installation != null && SUPPORTED_MEDIA.contains(installation.getMediaSetId());
        } catch (LinkageError | RuntimeException e) {
            return false;
        }
    }

    private static ApplicationRegistry.ApplicationInfo installation() {
        return ApplicationRegistry.getApplicationInfoByDir(Path.of(System.getProperty("install4j.appDir")).toFile());
    }

    static Target check() throws Exception {
        List<String> sources = sources();
        IOException failure = new IOException("All update descriptor sources failed");
        for (String source : sources) {
            try {
                return check(source, sources);
            } catch (IOException | RuntimeException e) {
                failure.addSuppressed(e);
            }
        }
        throw failure;
    }

    private static List<String> sources() {
        var sources = new LinkedHashSet<String>();
        sources.add(PRIMARY);
        var config = Main.getMainConfig();
        var mirrors = config.contains("updates.install4j-mirrors")
                ? config.getStringList("updates.install4j-mirrors") : List.of(DEFAULT_MIRROR);
        for (String mirror : mirrors) {
            String value = mirror.strip();
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("Install4j update mirrors must be HTTPS base URLs");
            }
            sources.add(value.endsWith("/") ? value : value + "/");
        }
        return List.copyOf(sources);
    }

    private static Target check(String source, List<String> sources) throws Exception {
        var descriptor = UpdateChecker.getUpdateDescriptor(new UpdateCheckRequest(source + "updates.xml")
                .applicationDisplayMode(ApplicationDisplayMode.UNATTENDED)
                .connectTimeout(15000).readTimeout(15000).askForProxy(false));
        UpdateDescriptorEntry entry = descriptor.getPossibleUpdateEntry();
        if (entry == null || entry.isArchive()
                || !UpdateChecker.isVersionGreaterThan(entry.getNewVersion(), Main.getMeta().getVersion())) {
            return null;
        }
        String name = entry.getFileName();
        String sha256 = entry.getSha256Sum();
        if (!name.matches("[A-Za-z0-9._-]+") || ".".equals(name) || "..".equals(name)
                || sha256 == null || !sha256.matches("[a-fA-F0-9]{64}") || entry.getFileSize() <= 0) {
            throw new IOException("Incomplete update metadata for " + name);
        }
        if (!entry.getTargetMediaFileId().equals(entry.getNewMediaFileId())) {
            throw new IOException("Unexpected change of installer media ID");
        }
        var downloadSources = new ArrayList<>(sources);
        downloadSources.remove(source);
        downloadSources.addFirst(source);
        return new Target(entry.getNewVersion(), entry.getNewMediaFileId(), name, entry.getComment(),
                entry.getFileSize(), sha256, List.copyOf(downloadSources));
    }

    static Path download(Target target) throws Exception {
        var directory = Files.createTempDirectory("pbh-install4j-update-");
        var file = directory.resolve(target.name());
        IOException failure = new IOException("All installer download sources failed");
        try {
            for (String base : target.sources()) {
                String source = base;
                if (DEFAULT_MIRROR.equals(base) && target.version().matches("[0-9A-Za-z.+-]+")) {
                    source = base.replace("/latest/download/", "/download/v" + target.version() + "/");
                }
                try {
                    download(target, source, file);
                    return file;
                } catch (IOException e) {
                    failure.addSuppressed(e);
                    Files.deleteIfExists(file);
                }
            }
            throw failure;
        } catch (Exception e) {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
            throw e;
        }
    }

    private static void download(Target target, String source, Path file) throws Exception {
        URI uri = URI.create(source).resolve(target.name());
        try (var response = HTTP.newCall(new Request.Builder().url(uri.toString()).build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Installer download returned HTTP " + response.code());
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var stream = new DigestInputStream(response.body().byteStream(), digest)) {
                long size = Files.copy(stream, file);
                if (size != target.size() || !target.sha256().equalsIgnoreCase(HexFormat.of().formatHex(digest.digest()))) {
                    throw new IOException("Installer checksum or size does not match");
                }
            }
        }
    }

    static void install(Target target, Path file, Runnable failed) throws IOException {
        var installed = installation();
        if (installed == null || !UpdateChecker.isVersionGreaterThan(target.version(), installed.getVersion())) {
            throw new IOException("The installed version changed while the update was downloading");
        }
        var arguments = List.of("-q", "-VpbhCallerPid=" + ProcessHandle.current().pid(), "-VpbhManagedInstaller=" + file,
                "-VpbhManagedVersion=" + target.version(), "-VpbhManagedMedia=" + target.media(),
                "-VpbhManagedSha256=" + target.sha256(),
                "-VpbhRestartLauncher=" + System.getProperty("pbh.install4j.launcher", ""));
        ApplicationLauncher.launchApplication("352", arguments.toArray(String[]::new), false,
                new ApplicationLauncher.Callback() {
                    @Override
                    public void exited(int exitValue) {
                        try {
                            Files.deleteIfExists(file);
                            Files.deleteIfExists(file.getParent());
                        } catch (IOException ignored) {
                            // The updater also cleans the staging directory after it exits.
                        }
                        failed.run();
                    }

                    @Override
                    public void prepareShutdown() {
                        // PBH's existing JVM shutdown hook closes the server and GUI.
                    }
                });
    }
}
