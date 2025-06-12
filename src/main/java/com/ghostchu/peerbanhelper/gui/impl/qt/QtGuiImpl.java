//package com.ghostchu.peerbanhelper.gui.impl.qt;
//
//import com.ghostchu.peerbanhelper.ExternalSwitch;
//import com.ghostchu.peerbanhelper.Main;
//import com.ghostchu.peerbanhelper.PeerBanHelper;
//import com.ghostchu.peerbanhelper.event.PBHLookAndFeelNeedReloadEvent;
//import com.ghostchu.peerbanhelper.exchange.ExchangeMap;
//import com.ghostchu.peerbanhelper.gui.ProgressDialog;
//import com.ghostchu.peerbanhelper.gui.TaskbarControl;
//import com.ghostchu.peerbanhelper.gui.impl.console.ConsoleGuiImpl;
//import com.ghostchu.peerbanhelper.gui.impl.qt.tabs.QtLogsTabComponent;
//import com.ghostchu.peerbanhelper.gui.impl.swing.theme.PBHFlatLafTheme;
//import com.ghostchu.peerbanhelper.gui.impl.swing.theme.impl.StandardLafTheme;
//import com.ghostchu.peerbanhelper.text.Lang;
//import com.ghostchu.peerbanhelper.util.CommonUtil;
//import com.ghostchu.peerbanhelper.util.logger.JListAppender;
//import com.google.common.eventbus.Subscribe;
//import io.qt.core.QMetaObject;
//import io.qt.core.QUrl;
//import io.qt.core.Qt;
//import io.qt.gui.QDesktopServices;
//import io.qt.widgets.QApplication;
//import io.qt.widgets.QMessageBox;
//import lombok.Getter;
//import lombok.SneakyThrows;
//import lombok.extern.slf4j.Slf4j;
//import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
//import org.apache.commons.compress.archivers.zip.ZipFile;
//import org.apache.commons.io.IOUtils;
//import org.jetbrains.annotations.Nullable;
//import org.slf4j.event.Level;
//
//import java.io.*;
//import java.net.URI;
//import java.util.*;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//import java.util.concurrent.Future;
//import java.util.concurrent.TimeUnit;
//import java.util.concurrent.atomic.AtomicBoolean;
//
//import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;
//
//@Getter
//@Slf4j
//public final class QtGuiImpl extends ConsoleGuiImpl {
//    @Getter
//    private final boolean silentStart;
//    private QtMainWindow qtMainWindow;
//    @Getter
//    private PBHFlatLafTheme pbhFlatLafTheme = new StandardLafTheme();
//    @Getter
//    private QtTaskbarControl qtTaskbarControl;
//    @Getter
//    private QApplication application;
//
//    public QtGuiImpl(String[] args) {
//        super(args);
//        System.setProperty("java.awt.headless", "true");
//        this.silentStart = Arrays.stream(args).anyMatch(s -> s.equalsIgnoreCase("silent"));
//        // 设置应用程序属性
//        setupEnv();
//        log.info(tlUI(Lang.GUI_QT6_WAITING_INIT));
//        QApplication.setApplicationName("PeerBanHelper");
//        QApplication.setApplicationVersion(Main.getMeta().getVersion());
//        QApplication.setOrganizationName("PeerBanHelper");
//        QApplication.setOrganizationDomain("peerbanhelper.com");
//        QApplication.initialize(args);
//        // 初始化Qt应用程序
//        this.application = QApplication.instance();
//    }
//
//    @SneakyThrows
//    private void setupEnv() {
//        String platform = "windows-x64";
//        File qt6Natives = new File("natives", "qt6");
//        File[] nativesJars = new File(qt6Natives, platform).listFiles((f) -> f.getName().endsWith(".jar"));
//        if (nativesJars == null) {
//            throw new IllegalStateException(tlUI(Lang.GUI_QT6_NATIVES_MISSING));
//        }
//        long modifyAt = Arrays.stream(nativesJars)
//                .mapToLong(File::lastModified)
//                .max()
//                .orElse(0);
//        long totalSize = Arrays.stream(nativesJars)
//                .mapToLong(File::length)
//                .sum();
//        String directoryName = "peerbanhelper-qt6-natives-" + modifyAt + "-" + totalSize;
//        log.info(tlUI(Lang.GUI_QT6_WAITING_EXTRACTING), directoryName);
//        long startAt = System.currentTimeMillis();
//        File qt6NativeExtractTo = new File(System.getProperty("java.io.tmpdir"), directoryName);
//        if (!qt6NativeExtractTo.exists())
//            if (!qt6NativeExtractTo.mkdirs())
//                throw new IllegalStateException("Failed to create directory for Qt6 natives: " + qt6NativeExtractTo.getAbsolutePath());
//        try (ExecutorService decompressExecutor = Executors.newWorkStealingPool(Runtime.getRuntime().availableProcessors())) {
//            for (File nativeJar : nativesJars) {
//                // decompress zip file in thread pool
//                try (ZipFile zipFile = ZipFile.builder().setFile(nativeJar).get()) {
//                    List<Future<Boolean>> futures = new ArrayList<>();
//                    var e = zipFile.getEntries();
//                    while (e.hasMoreElements()) {
//                        ZipArchiveEntry zipEntry = e.nextElement();
//                        Future<Boolean> future = decompressExecutor.submit(() -> {
//                            if (!zipEntry.getName().contains("META-INF") && !zipEntry.getName().contains("include")) {
//                                unZipFile(zipFile, zipEntry, qt6NativeExtractTo);
//                            }
//                            return true;
//                        });
//                        futures.add(future);
//                    }
//                    // blocking and caching exception
//                    for (var future : futures) {
//                        future.get();
//                    }
//                }
//            }
//        }
//        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
//        if (os.startsWith("win")) {
//            System.setProperty("java.library.path", System.getProperty("java.library.path") + ";" + new File(qt6NativeExtractTo, "bin").getAbsolutePath());
//        } else {
//            System.setProperty("java.library.path", System.getProperty("java.library.path") + ":" + new File(qt6NativeExtractTo, "lib").getAbsolutePath());
//        }
//        System.setProperty("java.library.path", new File(qt6NativeExtractTo, "bin").getAbsolutePath());
//        log.info("Qt6 natives extracted to: {}, cost: {}ms", qt6NativeExtractTo.getAbsolutePath(), System.currentTimeMillis() - startAt);
//    }
//
//    private void unZipFile(ZipFile zipFile, ZipArchiveEntry entry, File targetDir) throws Exception {
//        File targetFile = new File(targetDir, entry.getName());
//        targetFile.getParentFile().mkdirs();
//        try (InputStream inputStream = zipFile.getInputStream(entry);
//             BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
//             BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(targetFile))) {
//            IOUtils.copy(bufferedInputStream, bufferedOutputStream);
//        }
//    }
//
//    private void updateGuiStuff() {
//        StringBuilder builder = new StringBuilder();
//        builder.append(tlUI(Lang.GUI_TITLE_LOADED, "Qt6 UI", Main.getMeta().getVersion(), Main.getMeta().getAbbrev()));
//        StringJoiner joiner = new StringJoiner("", " [", "]");
//        joiner.setEmptyValue("");
//        ExchangeMap.GUI_DISPLAY_FLAGS.forEach(flag -> joiner.add(flag.getContent()));
//        String finalTitle = builder.append(joiner).toString();
//        if (qtMainWindow != null) {
//            QMetaObject.invokeMethod(qtMainWindow, () -> qtMainWindow.setWindowTitle(finalTitle), Qt.ConnectionType.QueuedConnection);
//        }
//    }
//
//    @Override
//    public void setup() {
//        super.setup();
//        createMainWindow(); // 创建主窗口
//        Main.getEventBus().register(this);
//    }
//
//    @Override
//    public void createMainWindow() {
//        qtMainWindow = new QtMainWindow(this);
//        qtTaskbarControl = new QtTaskbarControl(qtMainWindow);
//        initLoggerRedirection();
//    }
//
//
//    @Override
//    public String getName() {
//        return "Qt";
//    }
//
//    @Override
//    public boolean supportInteractive() {
//        return true;
//    }
//
//    @Override
//    public void createYesNoDialog(Level level, String title, String description, @Nullable Runnable yesEvent,
//                                  @Nullable Runnable noEvent) {
//        if (qtMainWindow != null) {
//            qtMainWindow.activateWindow();
//            qtMainWindow.raise();
//        }
//        QMetaObject.invokeMethod(qtMainWindow, () -> {
//            QMessageBox messageBox = new QMessageBox();
//            messageBox.setWindowTitle(title);
//            messageBox.setText(description);
//            messageBox.setStandardButtons(QMessageBox.StandardButton.Yes, QMessageBox.StandardButton.No);
//
//            if (level == Level.INFO) {
//                messageBox.setIcon(QMessageBox.Icon.Information);
//            } else if (level == Level.WARN) {
//                messageBox.setIcon(QMessageBox.Icon.Warning);
//            } else if (level == Level.ERROR) {
//                messageBox.setIcon(QMessageBox.Icon.Critical);
//            }
//            int result = messageBox.exec();
//            if (result == QMessageBox.StandardButton.Yes.value()) {
//                if (yesEvent != null) yesEvent.run();
//            } else if (result == QMessageBox.StandardButton.No.value()) {
//                if (noEvent != null) noEvent.run();
//            }
//        }, Qt.ConnectionType.QueuedConnection);
//    }
//
//    @Override
//    public void onPBHFullyStarted(PeerBanHelper server) {
//        CommonUtil.getScheduler().scheduleWithFixedDelay(this::updateGuiStuff, 0, 1, TimeUnit.SECONDS);
//    }
//
//    @Subscribe
//    public void needReloadThemes(PBHLookAndFeelNeedReloadEvent event) {
//        // 主题重载事件处理
//    }
//
//    @Override
//    public boolean isGuiAvailable() {
//        return true;
//    }
//
//    @Override
//    public ProgressDialog createProgressDialog(String title, String description, String buttonText,
//                                               Runnable buttonEvent, boolean allowCancel) {
//        return new QtProgressDialog(title, description, buttonText, buttonEvent, allowCancel);
//    }
//
//    @Override
//    public TaskbarControl taskbarControl() {
//        if (qtTaskbarControl != null) {
//            return qtTaskbarControl;
//        }
//        return super.taskbarControl();
//    }
//
//    private void initLoggerRedirection() {
//        QtLogsTabComponent logsTabComponent = qtMainWindow.getLogsTabComponent();
//        AtomicBoolean autoScroll = new AtomicBoolean(true);
//        JListAppender.allowWriteLogEntryDeque.set(true);
//        var maxSize = ExternalSwitch.parseInt("pbh.gui.logs.maxSize", 300);
//        CommonUtil.getScheduler().scheduleWithFixedDelay(() -> {
//            QMetaObject.invokeMethod(qtMainWindow, () -> {
//                while (!JListAppender.logEntryDeque.isEmpty()) {
//                    var logEntry = JListAppender.logEntryDeque.poll();
//                    if (logEntry == null) break;
//                    logsTabComponent.addLogEntry(logEntry.content(), logEntry.level());
//                    if (autoScroll.get()) {
//                        logsTabComponent.scrollToBottom();
//                    }
//                }
//                logsTabComponent.limitLogEntries(maxSize);
//            }, Qt.ConnectionType.QueuedConnection);
//        }, 0, 10, TimeUnit.MILLISECONDS);
//    }
//
//
//    @Override
//    public void sync() {
//        // Qt事件循环在sync()中启动
//        QApplication.exec();
//    }
//
//    @Override
//    public void close() {
//        if (application != null) {
//            QMetaObject.invokeMethod(qtMainWindow, QApplication::quit, Qt.ConnectionType.QueuedConnection);
//        }
//    }
//
//    @Override
//    public void createDialog(Level level, String title, String description, Runnable clickEvent) {
//        if (qtMainWindow != null) {
//            qtMainWindow.activateWindow();
//            qtMainWindow.raise();
//        }
//        QMetaObject.invokeMethod(qtMainWindow, () -> {
//            QMessageBox messageBox = new QMessageBox();
//            messageBox.setWindowTitle(title);
//            messageBox.setText(description);
//            messageBox.setStandardButtons(QMessageBox.StandardButton.Ok);
//
//            if (level == Level.INFO) {
//                messageBox.setIcon(QMessageBox.Icon.Information);
//            } else if (level == Level.WARN) {
//                messageBox.setIcon(QMessageBox.Icon.Warning);
//            } else if (level == Level.ERROR) {
//                messageBox.setIcon(QMessageBox.Icon.Critical);
//            }
//
//            messageBox.exec();
//            if (clickEvent != null) {
//                clickEvent.run();
//            }
//        }, Qt.ConnectionType.QueuedConnection);
//    }
//
//    @Override
//    public void createNotification(Level level, String title, String description) {
//        if (qtMainWindow != null && qtMainWindow.getTrayManager() != null) {
//            QMetaObject.invokeMethod(qtMainWindow, () -> {
//                qtMainWindow.getTrayManager().createNotification(level, title, description);
//            }, Qt.ConnectionType.QueuedConnection);
//        } else {
//            super.createNotification(level, title, description);
//        }
//    }
//
//    public void openWebpage(URI uri) {
//        QMetaObject.invokeMethod(qtMainWindow, () -> {
//            QDesktopServices.openUrl(new QUrl(uri.toString()));
//
//        }, Qt.ConnectionType.QueuedConnection);
//
//    }
//}
