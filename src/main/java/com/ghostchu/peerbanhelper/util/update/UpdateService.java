package com.ghostchu.peerbanhelper.util.update;

import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.backgroundtask.BackgroundTaskManager;
import com.ghostchu.peerbanhelper.util.backgroundtask.FunctionalBackgroundTask;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vdurmont.semver4j.Semver;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public final class UpdateService {
    public enum Mode { MANUAL, AUTOMATIC, DISABLED }
    public record Release(String id, String version, String url, String changeLog, Long size, boolean installable) {}
    public record Status(Mode mode, String deployment, boolean canInstall, String currentVersion,
                         String state, Release release, String checkedAt) {}

    private static final String MODE_KEY = "updates.mode";
    private static final String RELEASE_API =
            "https://api.github.com/repos/PBH-BTN/PeerBanHelper/releases/latest";
    private final HTTPUtil http;
    private final BackgroundTaskManager tasks;
    private final AtomicBoolean busy = new AtomicBoolean();
    private volatile Candidate candidate;
    private volatile Instant checkedAt;
    private volatile String state = "IDLE";

    private record DockerImages(String hub, String mirror) {}
    private record Candidate(Release release, Install4jUpdates.Target installer, DockerImages images) {}

    public UpdateService(HTTPUtil http, BackgroundTaskManager tasks) {
        this.http = http;
        this.tasks = tasks;
    }

    private Mode mode() {
        try {
            return Mode.valueOf(Main.getMainConfig().getString(MODE_KEY, "MANUAL"));
        } catch (IllegalArgumentException e) {
            return Mode.MANUAL;
        }
    }

    private String deployment() {
        if ("true".equalsIgnoreCase(System.getenv("PBH_PKG_SYNOLOGY"))) {
            return "spk";
        }
        String release = ExternalSwitch.parse("pbh.release", "jar");
        if ("install4j".equals(release) && System.getProperty("install4j.appDir") == null) {
            return "jar";
        }
        return release;
    }

    private boolean canInstall(String deployment) {
        return switch (deployment) {
            case "install4j" -> Install4jUpdates.isSupported();
            case "docker" -> dockerUpdaterAvailable();
            default -> false;
        };
    }

    private boolean dockerUpdaterAvailable() {
        try {
            var heartbeat = Files.getLastModifiedTime(dockerDirectory().resolve("enabled")).toInstant();
            return heartbeat.plusSeconds(30).isAfter(Instant.now()) || Files.exists(dockerDirectory().resolve("working"));
        } catch (IOException e) {
            return false;
        }
    }

    private Path dockerDirectory() {
        return Main.getDataDirectory().toPath().resolve("docker-update");
    }

    public Status status() {
        String deployment = deployment();
        Mode mode = mode();
        Candidate current = candidate;
        return new Status(mode, deployment, canInstall(deployment), Main.getMeta().getVersion(),
                mode == Mode.DISABLED ? "DISABLED" : state,
                mode == Mode.DISABLED || current == null ? null : current.release(),
                checkedAt == null ? null : checkedAt.toString());
    }

    public synchronized void configure(Mode mode) throws IOException {
        if (mode == null || mode == Mode.AUTOMATIC && !canInstall(deployment())) {
            throw new IllegalArgumentException("Unsupported update mode");
        }
        var config = Main.getMainConfig();
        String previous = config.getString(MODE_KEY);
        config.set(MODE_KEY, mode.name());
        try {
            config.save(Main.getMainConfigFile());
        } catch (IOException e) {
            config.set(MODE_KEY, previous);
            throw e;
        }
        if (mode == Mode.DISABLED) {
            candidate = null;
            Files.deleteIfExists(dockerDirectory().resolve("request"));
            Files.deleteIfExists(dockerDirectory().resolve("working"));
            if ("docker".equals(deployment())) {
                state = "IDLE";
            }
        } else {
            check();
        }
    }

    @Scheduled(initialDelay = 5000, fixedDelay = 21600000)
    public void scheduledCheck() {
        check();
    }

    public synchronized boolean check() {
        if (mode() == Mode.DISABLED || "INSTALLING".equals(state) || !busy.compareAndSet(false, true)) {
            return false;
        }
        state = "CHECKING";
        tasks.addTask(new FunctionalBackgroundTask(new TranslationComponent(Lang.SOFTWARE_UPDATE_CHECK), (task, progress) -> {
            try {
                Candidate found = lookup();
                synchronized (this) {
                    candidate = mode() == Mode.DISABLED ? null : found;
                    checkedAt = Instant.now();
                    state = candidate == null ? "CURRENT" : "AVAILABLE";
                    busy.set(false);
                    if (candidate != null && mode() == Mode.AUTOMATIC && canInstall(deployment())) {
                        install(candidate.release().id(), true);
                    }
                }
            } catch (Exception e) {
                log.warn("Update check failed", e);
                candidate = null;
                checkedAt = Instant.now();
                state = "CHECK_FAILED";
                busy.set(false);
                throw e;
            }
        }));
        return true;
    }

    private Candidate lookup() throws Exception {
        String deployment = deployment();
        if ("install4j".equals(deployment) && Install4jUpdates.isSupported()) {
            var installer = Install4jUpdates.check();
            if (installer == null) {
                return null;
            }
            return new Candidate(new Release(UUID.randomUUID().toString(), installer.version(),
                    "https://github.com/PBH-BTN/PeerBanHelper/releases", installer.comment(),
                    installer.size(), true), installer, null);
        }
        JsonObject json;
        try {
            json = readMetadata(RELEASE_API);
        } catch (IOException | RuntimeException first) {
            try {
                json = readMetadata("https://pbh-files.pbh-btn.com/updates/latest/release.json");
            } catch (IOException | RuntimeException second) {
                second.addSuppressed(first);
                throw second;
            }
        }
        String version = json.get("tag_name").getAsString();
        if (!isNewerRelease(version, Main.getMeta().getVersion())) {
            return null;
        }
        String notes = json.has("body") && !json.get("body").isJsonNull()
                ? json.get("body").getAsString() : "";
        DockerImages images = null;
        if ("docker".equals(deployment) && version.matches("v?\\d+\\.\\d+\\.\\d+")) {
            try {
                var manifest = readDockerMetadata(version);
                String hub = manifest.get("hub").getAsString();
                String mirror = manifest.get("mirror").getAsString();
                if (version.equals(manifest.get("version").getAsString())
                        && hub.matches("sha256:[a-f0-9]{64}") && mirror.matches("sha256:[a-f0-9]{64}")) {
                    images = new DockerImages(hub, mirror);
                }
            } catch (IOException | RuntimeException e) {
                log.debug("Published Docker update digests are unavailable for {}", version, e);
            }
        }
        return new Candidate(new Release(UUID.randomUUID().toString(), version,
                json.get("html_url").getAsString(), notes, null, images != null), null, images);
    }

    private JsonObject readDockerMetadata(String version) throws IOException {
        try {
            return readMetadata("https://github.com/PBH-BTN/PeerBanHelper/releases/download/"
                    + version + "/docker-update.json");
        } catch (IOException | RuntimeException e) {
            return readMetadata("https://pbh-files.pbh-btn.com/updates/docker/"
                    + version + "/docker-update.json");
        }
    }

    private JsonObject readMetadata(String url) throws IOException {
        try (var response = http.newBuilder().build().newCall(new Request.Builder().url(url)
                .header("Accept", "application/vnd.github+json").build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Update metadata returned HTTP " + response.code());
            }
            return JsonParser.parseString(response.body().string()).getAsJsonObject();
        }
    }

    private boolean isNewerRelease(String tag, String installed) {
        var target = new Semver(tag.replaceFirst("^v", ""));
        var current = new Semver(installed.replaceFirst("^v", ""));
        return target.isGreaterThan(current);
    }

    public synchronized boolean install(String id) {
        return install(id, false);
    }

    private synchronized boolean install(String id, boolean automatic) {
        Candidate selected = candidate;
        String deployment = deployment();
        if (!installationAllowed(automatic) || selected == null || !selected.release().id().equals(id)
                || !selected.release().installable() || !canInstall(deployment) || "INSTALLING".equals(state) || !busy.compareAndSet(false, true)) {
            return false;
        }
        state = "INSTALLING";
        tasks.addTask(new FunctionalBackgroundTask(new TranslationComponent(Lang.SOFTWARE_UPDATE_PREPARE), (task, progress) -> {
            try {
                synchronized (this) {
                    if (!installationAllowed(automatic)) {
                        state = "IDLE";
                        return;
                    }
                    if ("docker".equals(deployment)) {
                        requestDockerUpdate(selected);
                        return;
                    }
                }
                var file = Install4jUpdates.download(selected.installer());
                boolean handedOff = false;
                try {
                    synchronized (this) {
                        if (installationAllowed(automatic)) {
                            Install4jUpdates.install(selected.installer(), file, () -> state = "INSTALL_FAILED");
                            handedOff = true;
                        } else {
                            state = "IDLE";
                        }
                    }
                } finally {
                    if (!handedOff) {
                        Files.deleteIfExists(file);
                        Files.deleteIfExists(file.getParent());
                    }
                }
            } catch (Exception e) {
                log.warn("Update installation failed", e);
                state = "INSTALL_FAILED";
                throw e;
            } finally {
                busy.set(false);
            }
        }));
        return true;
    }

    private boolean installationAllowed(boolean automatic) {
        Mode mode = mode();
        return mode != Mode.DISABLED && (!automatic || mode == Mode.AUTOMATIC);
    }

    private void requestDockerUpdate(Candidate selected) throws IOException {
        var directory = dockerDirectory();
        if (Files.exists(directory.resolve("request")) || Files.exists(directory.resolve("working"))) {
            throw new IOException("A Docker update is already pending");
        }
        Files.deleteIfExists(directory.resolve("failed"));
        var temporary = Files.createTempFile(directory, "request-", ".tmp");
        try {
            Files.writeString(temporary, String.join("\n", selected.release().version(),
                    Main.getMeta().getVersion(), selected.images().hub(), selected.images().mirror()) + "\n");
            Files.move(temporary, directory.resolve("request"), java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @Scheduled(fixedDelay = 2000)
    public void readDockerResult() {
        if (!"docker".equals(deployment())) {
            return;
        }
        var result = dockerDirectory().resolve("failed");
        try {
            if (Files.deleteIfExists(result) || "INSTALLING".equals(state) && !dockerUpdaterAvailable()) {
                state = "INSTALL_FAILED";
            }
        } catch (IOException e) {
            log.warn("Unable to read Docker update result", e);
        }
    }
}
