package com.ghostchu.peerbanhelper.util;

import com.alessiodp.libby.Library;
import com.alessiodp.libby.LibraryManager;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.ghostchu.peerbanhelper.gui.crossimpl.CrossDownloaderDialog;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.maven.GeoUtil;
import com.ghostchu.peerbanhelper.util.maven.MavenCentralMirror;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class PBHLibrariesLoader {
    private final LibraryManager manager;
    private final Path librariesPath;
    private boolean repoAdded = false;

    public PBHLibrariesLoader(LibraryManager manager, Path librariesPath) {
        this.manager = manager;
        this.librariesPath = librariesPath;

    }

    private void addRepositories() {
        if (repoAdded) return;
        FlatIntelliJLaf.setup();
        CrossDownloaderDialog downloaderDialog = new CrossDownloaderDialog();
        downloaderDialog.setTitle(Lang.LIBRARIES_DOWNLOAD_DIALOG_TEST_SERVER);
        downloaderDialog.getTaskTitle().setText(Lang.LIBRARIES_DOWNLOAD_DIALOG_TEST_SERVER);
        downloaderDialog.getTooltip().setText(Lang.LIBRARIES_DOWNLOAD_DIALOG_TOOLTIP);
        downloaderDialog.getDescription().setText(String.format(Lang.LIBRARIES_DOWNLOAD_DIALOG_TEST_SERVER_DESCRIPTION, MavenCentralMirror.values().length));
        downloaderDialog.getProgressBar().setIndeterminate(true);
        downloaderDialog.getProgressBar().setString(Lang.LIBRARIES_DOWNLOAD_DIALOG_TEST_SERVER_BAR_TEXT);
        downloaderDialog.getProgressBar().setStringPainted(true);
        downloaderDialog.setVisible(true);
        try {
            List<MavenCentralMirror> mirrors = GeoUtil.determineBestMirrorServer(log);
            // 全部测试失败，这得是什么网络……
            if (mirrors.isEmpty()) {
                for (MavenCentralMirror value : MavenCentralMirror.values()) {
                    manager.addRepository(value.getRepoUrl());
                    repoAdded = true;
                }
            } else {
                manager.addRepository(mirrors.getFirst().getRepoUrl());
            }
        } finally {
            downloaderDialog.dispose();
        }
    }

    public void loadLibraries(List<String> libraries, Map<String, String> env) throws RuntimeException {
        FlatIntelliJLaf.setup();
        CrossDownloaderDialog downloaderDialog = new CrossDownloaderDialog();
        downloaderDialog.setTitle(Lang.LIBRARIES_DOWNLOAD_DIALOG_TITLE);
        downloaderDialog.getTaskTitle().setText(Lang.LIBRARIES_DOWNLOAD_DIALOG_TITLE);
        downloaderDialog.getTooltip().setText(Lang.LIBRARIES_DOWNLOAD_DIALOG_TOOLTIP);
        downloaderDialog.getProgressBar().setValue(0);
        try {
            loadLibraries0(libraries, env, (dependency, pos, total) -> {
                SwingUtilities.invokeLater(() -> {
                    if (!downloaderDialog.isVisible()) {
                        downloaderDialog.setVisible(true);
                    }
                    downloaderDialog.getDescription().setText(String.format(Lang.LIBRARIES_DOWNLOAD_DIALOG_DESCRIPTION, dependency));
                    downloaderDialog.getProgressBar().setString(String.format(Lang.LIBRARIES_DOWNLOAD_DIALOG_BAR_TEXT, pos, total));
                    downloaderDialog.getProgressBar().setMaximum(total);
                    downloaderDialog.getProgressBar().setValue(pos);
                });
            });
        } finally {
            downloaderDialog.dispose();
        }
    }

    private void loadLibraries0(List<String> libraries, Map<String, String> env, ProgressCallback callbackConsumer) throws RuntimeException {
        List<Library> libraryList = new ArrayList<>();
        int skipped = 0;
        for (String library : libraries) {
            if (library.isBlank() || library.startsWith("#") || library.startsWith("//")) continue;
            library = library.trim();
            String[] cases = library.split("@");
            String testClass = null;
            if (cases.length == 2) {
                testClass = cases[1];
            }
            if (testClass != null) {
                if (MiscUtil.isClassAvailable(testClass)) {
                    skipped++;
                    continue;
                }
            }
            String[] libExplode = cases[0].split(":");
            if (libExplode.length < 3) {
                throw new IllegalArgumentException("[" + library + "] not a valid maven dependency syntax");
            }
            for (int i = 0; i < libExplode.length; i++) {
                libExplode[i] = libExplode[i].trim();
                for (Map.Entry<String, String> pair : env.entrySet()) {
                    libExplode[i] = libExplode[i].replace("{" + pair.getKey() + "}", pair.getValue());
                }
            }
            String groupId = libExplode[0];
            String artifactId = libExplode[1];
            String version = libExplode[2];
            String classifier = null;
            if (libExplode.length >= 4) {
                classifier = libExplode[3];
            }
            Library.Builder libBuilder = Library.builder()
                    .groupId(groupId)
                    .artifactId(artifactId)
                    .version(version)
                    .resolveTransitiveDependencies(false)
                    .isolatedLoad(false);
            if (classifier != null) {
                libBuilder = libBuilder.classifier(classifier);
            }
            Library lib = libBuilder.build();
            if (Files.exists(librariesPath.resolve(lib.getPath())) && !lib.isSnapshot()) {
                manager.loadLibrary(lib);
                continue;
            }
            libraryList.add(lib);
        }
        if (libraryList.isEmpty()) {
            return; // Do not trigger callback because it will open a download window
        }
        addRepositories();
        log.info("Loading {} libraries ({} skipped libraries)...", libraryList.size(), skipped);
        for (int i = 0; i < libraryList.size(); i++) {
            Library load = libraryList.get(i);
            log.info("Loading library {} ... [{}/{}]", load.toString(), (i + 1), libraryList.size());
            callbackConsumer.callback(load.toString(), (i + 1), libraryList.size() + 1);
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            manager.loadLibrary(load);
        }
        callbackConsumer.callback("Done!", libraryList.size() + 1, libraryList.size() + 1);
    }

    interface ProgressCallback {
        void callback(String dependency, int pos, int total);
    }
}
