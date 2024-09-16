package com.ghostchu.peerbanhelper.gui.impl.javafx;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import atlantafx.base.theme.Styles;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.MainJavaFx;
import com.ghostchu.peerbanhelper.event.PBHServerStartedEvent;
import com.ghostchu.peerbanhelper.exchange.ExchangeMap;
import com.ghostchu.peerbanhelper.gui.impl.GuiImpl;
import com.ghostchu.peerbanhelper.gui.impl.console.ConsoleGuiImpl;
import com.ghostchu.peerbanhelper.gui.impl.javafx.mainwindow.JFXWindowController;
import com.ghostchu.peerbanhelper.log4j2.SwingLoggerAppender;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.MsgUtil;
import com.ghostchu.peerbanhelper.util.collection.CircularArrayList;
import com.google.common.eventbus.Subscribe;
import com.jthemedetecor.OsThemeDetector;
import com.pixelduke.window.ThemeWindowManager;
import com.pixelduke.window.ThemeWindowManagerFactory;
import com.sun.management.HotSpotDiagnosticMXBean;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import javax.management.MBeanServer;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Getter
@Slf4j
public class JavaFxImpl extends ConsoleGuiImpl implements GuiImpl {
    private static final int MAX_LINES = 300;
    //    private final AtomicBoolean needUpdate = new AtomicBoolean(false);
//    private String logsBuffer;
    private static final PseudoClass EMPTY = PseudoClass.getPseudoClass("empty");
    private static final PseudoClass FATAL = PseudoClass.getPseudoClass("fatal");
    private static final PseudoClass ERROR = PseudoClass.getPseudoClass("error");
    private static final PseudoClass WARN = PseudoClass.getPseudoClass("warn");
    private static final PseudoClass INFO = PseudoClass.getPseudoClass("info");
    private static final PseudoClass DEBUG = PseudoClass.getPseudoClass("debug");
    private static final PseudoClass TRACE = PseudoClass.getPseudoClass("trace");
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    @Getter
    private final boolean silentStart;
    private final String[] args;
    private final Set<ListCell<ListLogEntry>> selected = new HashSet<>();
    private final ScheduledExecutorService scheduledService = Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory());
    private TrayIcon trayIcon;
    private ListView<ListLogEntry> logsView;
    private boolean persistFlagTrayMessageSent = false;
    private final OsThemeDetector detector = OsThemeDetector.getDetector();

    public JavaFxImpl(String[] args) {
        super(args);
        this.args = args;
        this.silentStart = Arrays.stream(args).anyMatch(s -> s.equalsIgnoreCase("silent"));
        Main.getEventBus().register(this);
    }


    private boolean isWebViewSupported() {
        try {
            Class.forName("javafx.scene.web.WebView");
            return System.getProperty("enableWebView") != null;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }


    @Override
    public void setup() {
        super.setup();
        setupSystemTray();
    }

    @Subscribe
    public void onPBHServerStarted(PBHServerStartedEvent event) {
        //Platform.runLater(() -> MainJavaFx.getStage().setTitle(tlUI(Lang.GUI_TITLE_LOADED, "JavaFx", Main.getMeta().getVersion(), Main.getMeta().getAbbrev())));
        scheduledService.scheduleWithFixedDelay(this::updateTitleFlags, 0, 5, TimeUnit.SECONDS);
//        if (Arrays.stream(Main.getStartupArgs()).noneMatch(s -> s.equalsIgnoreCase("enableWebview"))) {
//            log.info(tlUI(Lang.WEBVIEW_DEFAULT_DISABLED));
//            return;
//        }
//        try {
//            Main.loadDependencies("/libraries/javafx-web.maven");
//            CompletableFuture.runAsync(() -> {
//                Platform.runLater(() -> {
//                    if (isWebViewSupported()) {
//                        JFXWindowController controller = MainJavaFx.INSTANCE.getController();
//                        Tab webuiTab = JavaFxWebViewWrapper.installWebViewTab(controller.getTabPane(), tlUI(Lang.GUI_MENU_WEBUI), Main.getServer().getWebUiUrl(), Collections.emptyMap(), null);
//                        javafx.scene.control.SingleSelectionModel<Tab> selectionModel = controller.getTabPane().getSelectionModel();
//                        selectionModel.select(webuiTab);
//                        log.info(tlUI(Lang.WEBVIEW_ENABLED));
//                    } else {
//                        log.info(tlUI(Lang.WEBVIEW_DISABLED_WEBKIT_NOT_INCLUDED));
//                    }
//                });
//            });
//        } catch (IOException e) {
//            log.error(tlUI(Lang.WEBVIEW_DISABLED_WEBKIT_NOT_INCLUDED), e);
//        }

    }

    public void updateTitleFlags() {
        StringJoiner joiner = new StringJoiner(" ", "[", "]");
        String base = tlUI(Lang.GUI_TITLE_LOADED, "JavaFx", Main.getMeta().getVersion(), Main.getMeta().getAbbrev());
        ExchangeMap.GUI_DISPLAY_FLAGS.forEach(flag -> joiner.add(flag.getContent()));
        Platform.runLater(() -> MainJavaFx.getStage().setTitle(base + " " + joiner));
    }

    @SneakyThrows
    @Override
    public void createMainWindow() {
        Platform.setImplicitExit(false);
        CompletableFuture.runAsync(() -> MainJavaFx.launchApp(args));
        while (!MainJavaFx.ready.get()) {
            Thread.sleep(50);
            Thread.yield();
        }
        Platform.runLater(this::setupJFXWindow);
    }

    private void setupJFXWindow() {
        Stage st = MainJavaFx.getStage();
        detector.registerListener(this::updateTheme);
        updateTheme(detector.isDark());
        st.getScene().getWindow().addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, this::closeWindowEvent);
        JFXWindowController controller = MainJavaFx.INSTANCE.getController();
        this.logsView = controller.getLogsListView();
        this.logsView.setStyle("-fx-font-family: Consolas, Monospace");
        this.logsView.setItems(FXCollections.observableList(new CircularArrayList<>(SwingLoggerAppender.maxLinesSetting + 1)));
        this.logsView.getItems().addListener((ListChangeListener<ListLogEntry>) change -> {
            while (logsView.getItems().size() > SwingLoggerAppender.maxLinesSetting) {
                logsView.getItems().removeFirst();
            }
        });
        Holder<Object> lastCell = new Holder<>();
        Styles.addStyleClass(this.logsView, Styles.DENSE);
        Styles.addStyleClass(this.logsView, Styles.STRIPED);
        this.logsView.setCellFactory(x -> new ListCell<>() {
            {
                getStyleClass().add("log-window-list-cell");
                Region clippedContainer = (Region) logsView.lookup(".clipped-container");
                if (clippedContainer != null) {
                    maxWidthProperty().bind(clippedContainer.widthProperty());
                    prefWidthProperty().bind(clippedContainer.widthProperty());
                }
                setPadding(new Insets(2));
                setWrapText(true);
                setGraphic(null);
                setOnMouseClicked(event -> {
                    if (!event.isControlDown()) {
                        for (ListCell<ListLogEntry> logListCell : selected) {
                            if (logListCell != this) {
                                logListCell.pseudoClassStateChanged(SELECTED, false);
                                if (logListCell.getItem() != null) {
                                    logListCell.getItem().setSelected(false);
                                }
                            }
                        }
                        selected.clear();
                    }

                    selected.add(this);
                    pseudoClassStateChanged(SELECTED, true);
                    if (getItem() != null) {
                        getItem().setSelected(true);
                    }
                });
            }

            @Override
            protected void updateItem(ListLogEntry item, boolean empty) {
                super.updateItem(item, empty);
                // https://mail.openjdk.org/pipermail/openjfx-dev/2022-July/034764.html
                if (this == lastCell.value && !isVisible())
                    return;
                lastCell.value = this;
                pseudoClassStateChanged(EMPTY, empty);
                pseudoClassStateChanged(ERROR, !empty && item.getLevel() == org.slf4j.event.Level.ERROR);
                pseudoClassStateChanged(WARN, !empty && item.getLevel() == org.slf4j.event.Level.WARN);
                pseudoClassStateChanged(INFO, !empty && item.getLevel() == org.slf4j.event.Level.INFO);
                pseudoClassStateChanged(DEBUG, !empty && item.getLevel() == org.slf4j.event.Level.DEBUG);
                pseudoClassStateChanged(TRACE, !empty && item.getLevel() == org.slf4j.event.Level.TRACE);
                pseudoClassStateChanged(SELECTED, !empty && item.isSelected());
                if (empty) {
                    setText(null);
                } else {
                    setText(item.getLog());
                }
            }
        });
        logsView.setOnKeyPressed(event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.C) {
                StringBuilder stringBuilder = new StringBuilder();

                for (ListLogEntry item : logsView.getItems()) {
                    if (item != null && item.isSelected()) {
                        if (item.getLog() != null) {
                            stringBuilder.append(item.getLog());
                        }
                        stringBuilder.append('\n');
                    }
                }
                String cont = stringBuilder.toString();
                JFXUtil.copyText(cont);
                createDialog(Level.INFO, tlUI(Lang.GUI_COPY_TO_CLIPBOARD_TITLE), tlUI(Lang.GUI_COPY_TO_CLIPBOARD_DESCRIPTION, cont));
            }
        });
        initLoggerRedirection();
        controller.getMenuProgram().setText(tlUI(Lang.GUI_MENU_PROGRAM));
        controller.getMenuWebui().setText(tlUI(Lang.GUI_MENU_WEBUI));
        controller.getTabLogs().setText(tlUI(Lang.GUI_TABBED_LOGS));
        controller.getMenuProgramQuit().setText(tlUI(Lang.GUI_MENU_QUIT));
        controller.getMenuProgramQuit().setOnAction(e -> System.exit(0));
        controller.getMenuProgramOpenInGithub().setText(tlUI(Lang.ABOUT_VIEW_GITHUB));
        controller.getMenuProgramOpenInGithub().setOnAction(e -> openWebpage(URI.create(tlUI(Lang.GITHUB_PAGE))));
        controller.getMenuWebUIOpenInBrowser().setText(tlUI(Lang.GUI_MENU_WEBUI_OPEN));
        controller.getMenuWebUIOpenInBrowser().setOnAction(e -> openWebpage(URI.create(Main.getServer().getWebUiUrl())));
        controller.getMenuWebUICopyWebuiToken().setText(tlUI(Lang.GUI_COPY_WEBUI_TOKEN));
        controller.getMenuWebUICopyWebuiToken().setOnAction(e -> {
            if (Main.getServer() != null && Main.getServer().getWebContainer() != null) {
                String content = Main.getServer().getWebContainer().getToken();
                JFXUtil.copyText(content);
                createDialog(Level.INFO, tlUI(Lang.GUI_COPY_TO_CLIPBOARD_TITLE), String.format(tlUI(Lang.GUI_COPY_TO_CLIPBOARD_DESCRIPTION, content)));
            }
        });
        controller.getMenuProgramOpenDataDirectory().setText(tlUI(Lang.GUI_MENU_OPEN_DATA_DIRECTORY));
        controller.getMenuProgramOpenDataDirectory().setOnAction(e -> {
            try {
                Desktop.getDesktop().open(Main.getDataDirectory());
            } catch (IOException ex) {
                log.warn("Unable to open data directory {} in desktop env.", Main.getDataDirectory().getPath());
            }
        });
        controller.getMenuDebug().setText(tlUI(Lang.GUI_MENU_DEBUG));
        controller.getMenuDebugReload().setText(tlUI(Lang.GUI_MENU_DEBUG_RELOAD_CONFIGURATION));
        controller.getMenuDebugReload().setOnAction(this::debugReload);
        controller.getMenuDebugHeapDump().setText(tlUI(Lang.GUI_MENU_DEBUG_HEAP_DUMP));
        controller.getMenuDebugHeapDump().setOnAction(this::debugHeapDump);
        controller.getMenuDebugPrintThreads().setText(tlUI(Lang.GUI_MENU_PRINT_THREADS));
        controller.getMenuDebugPrintThreads().setOnAction(this::debugPrintThreads);
        if (silentStart) {
            setVisible(false);
        }
    }

    private void debugPrintThreads(ActionEvent actionEvent) {
        StringBuilder threadDump = new StringBuilder(System.lineSeparator());
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        for (ThreadInfo threadInfo : threadMXBean.dumpAllThreads(true, true)) {
            threadDump.append(MsgUtil.threadInfoToString(threadInfo));
        }
        log.info(threadDump.toString());
    }

    private void debugHeapDump(ActionEvent actionEvent) {
        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            HotSpotDiagnosticMXBean mxBean = ManagementFactory.newPlatformMXBeanProxy(
                    server, "com.sun.management:type=HotSpotDiagnostic", HotSpotDiagnosticMXBean.class);
            File hprof = new File(Main.getDebugDirectory(), System.currentTimeMillis() + ".hprof");
            System.gc();
            mxBean.dumpHeap(hprof.getAbsolutePath(), true);
            createDialog(Level.INFO, tlUI(Lang.HEAPDUMP_COMPLETED_TITLE), tlUI(Lang.HEAPDUMP_COMPLETED_DESCRIPTION));
        } catch (Exception e) {
            log.error("Unable dump heap", e);
            createDialog(Level.SEVERE, tlUI(Lang.HEAPDUMP_FAILED_TITLE), tlUI(Lang.HEAPDUMP_FAILED_DESCRIPTION));
        }
    }

    private void debugReload(ActionEvent actionEvent) {
        var map = Main.getReloadManager().reload();
        map.forEach((c, r) -> {
            var name = "???";
            var reloadableWrapper = c.getReloadable();
            if (reloadableWrapper != null) {
                var content = reloadableWrapper.get();
                if (content != null) {
                    name = content.getClass().getName();
                } else {
                    name = "<Invalid>";
                }
            } else {
                name = c.getReloadableMethod().getName();
            }
            log.info(tlUI(Lang.RELOADING_MODULE, name, r.getStatus().name()));
        });
        createDialog(Level.INFO, tlUI(Lang.RELOAD_COMPLETED_TITLE), tlUI(Lang.RELOAD_COMPLETED_DESCRIPTION, map.size()));
    }

    private void updateTheme(boolean isDark) {
        Stage stage = MainJavaFx.getStage();
        Window window = stage.getScene().getWindow();
        ThemeWindowManager themeWindowManager = ThemeWindowManagerFactory.create();
//        if(themeWindowManager instanceof Win10ThemeWindowManager win10ThemeWindowManager){
//           // win10ThemeWindowManager.enableAcrylic(stage.getScene().getWindow(), );
//        }else if (themeWindowManager instanceof Win11ThemeWindowManager win11ThemeWindowManager){
//            win11ThemeWindowManager.setWindowBackdrop(window, Win11ThemeWindowManager.Backdrop.MICA);
//           // win11ThemeWindowManager.setWindowBorderColor(stage.getScene().getWindow(), Color.web("#4493f8"));
//        }
        themeWindowManager.setDarkModeForWindowFrame(window, isDark);
        stage.getScene().getRoot().getStylesheets().removeAll();
        stage.getScene().getRoot().getStylesheets().add(Main.class.getResource("/javafx/css/root.css").toExternalForm());
        if (isDark) {
            Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
            stage.getScene().getRoot().getStylesheets().add(Main.class.getResource("/javafx/css/dark.css").toExternalForm());
        } else {
            Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
            stage.getScene().getRoot().getStylesheets().add(Main.class.getResource("/javafx/css/light.css").toExternalForm());
        }

    }

    private void closeWindowEvent(WindowEvent windowEvent) {
        minimizeToTray();
    }

    private void minimizeToTray() {
        if (trayIcon != null) {
            setVisible(false);
            if (!persistFlagTrayMessageSent) {
                persistFlagTrayMessageSent = true;
                trayIcon.displayMessage(tlUI(Lang.GUI_TRAY_MESSAGE_CAPTION), tlUI(Lang.GUI_TRAY_MESSAGE_DESCRIPTION), TrayIcon.MessageType.INFO);
            }
        }
    }


    private void initLoggerRedirection() {
        SwingLoggerAppender.registerListener(loggerEvent -> {
            try {
                Platform.runLater(() -> {
                    logsView.getItems().add(new ListLogEntry(loggerEvent.message(), loggerEvent.level()));
                    if (!logsView.getItems().isEmpty()) {
                        logsView.scrollTo(logsView.getItems().size() - 1);
                    }
                });
            } catch (IllegalStateException exception) {
                exception.printStackTrace();
            }
        });
    }

    public boolean openWebpage(URI uri) {
        Desktop desktop = Desktop.getDesktop();
        if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
            try {
                desktop.browse(uri);
                return true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    @Override
    public void sync() {
        super.sync();
    }

    private void setupSystemTray() {
        if (SystemTray.isSupported()) {
            TrayIcon icon = new TrayIcon(Toolkit.getDefaultToolkit().getImage(Main.class.getResource("/assets/icon.png")));
            icon.setImageAutoSize(true);
            //创建弹出菜单
            PopupMenu menu = new PopupMenu();
            //添加一个用于退出的按钮
            MenuItem item = new MenuItem("Exit");
            item.addActionListener(e -> System.exit(0));
            menu.add(item);
            //添加弹出菜单到托盘图标
            icon.setPopupMenu(menu);
            SystemTray tray = SystemTray.getSystemTray();//获取系统托盘
            icon.addActionListener(e -> setVisible(true));
            try {
                tray.add(icon);//将托盘图表添加到系统托盘
                this.trayIcon = icon;
            } catch (AWTException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void setVisible(boolean b) {
        Platform.runLater(() -> {
            if (b) {
                MainJavaFx.getStage().show();
                updateTheme(detector.isDark());
            } else {
                MainJavaFx.getStage().hide();
            }
        });

    }

    @Override
    public void close() {
    }

    @Override
    public void createDialog(Level level, String title, String description) {
        Alert.AlertType alertType = Alert.AlertType.CONFIRMATION;
        if (level.equals(Level.WARNING)) {
            alertType = Alert.AlertType.WARNING;
        }
        if (level.equals(Level.SEVERE)) {
            alertType = Alert.AlertType.ERROR;
        }
        if (level.equals(Level.INFO)) {
            alertType = Alert.AlertType.INFORMATION;
        }
        Alert.AlertType finalAlertType = alertType;
        Platform.runLater(() -> {
            Alert alert = new Alert(finalAlertType);
            alert.setTitle(title);
            alert.setHeaderText(title);
            alert.setContentText(description);
            alert.show();
        });
    }

    @Override
    public void createNotification(Level level, String title, String description) {
        if (trayIcon != null) {
            TrayIcon.MessageType messageType = TrayIcon.MessageType.INFO;
            if (level.equals(Level.WARNING)) {
                messageType = TrayIcon.MessageType.WARNING;
            }
            if (level.equals(Level.SEVERE)) {
                messageType = TrayIcon.MessageType.ERROR;
            }
            trayIcon.displayMessage(title, description, messageType);
            return;
        }
        createDialog(level, title, description);
    }
}
