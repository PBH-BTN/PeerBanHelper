package com.ghostchu.peerbanhelper.gui.impl.javafx;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.MainJavaFx;
import com.ghostchu.peerbanhelper.event.PBHServerStartedEvent;
import com.ghostchu.peerbanhelper.gui.impl.GuiImpl;
import com.ghostchu.peerbanhelper.gui.impl.console.ConsoleGuiImpl;
import com.ghostchu.peerbanhelper.gui.impl.javafx.mainwindow.JFXWindowController;
import com.ghostchu.peerbanhelper.log4j2.SwingLoggerAppender;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.collection.CircularArrayList;
import com.google.common.eventbus.Subscribe;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.collections.FXCollections;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
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
    private final LinkedList<String> lines = new LinkedList<>();
    private final Set<ListCell<ListLogEntry>> selected = new HashSet<>();
    private TrayIcon trayIcon;
    private ListView<ListLogEntry> logsView;

    public JavaFxImpl(String[] args) {
        super(args);
        this.args = args;
        this.silentStart = Arrays.stream(args).anyMatch(s -> s.equalsIgnoreCase("silent"));
        Main.getEventBus().register(this);
    }


    private boolean isWebViewSupported() {
        try {
            Class.forName("javafx.scene.web.WebView");
            return true;
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
        if (Arrays.stream(Main.getStartupArgs()).anyMatch(s -> s.equalsIgnoreCase("nowebuitab"))) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            if (isWebViewSupported()) {
                Platform.runLater(() -> {
                    MainJavaFx.getStage().setTitle(tlUI(Lang.GUI_TITLE_LOADED, "JavaFx", Main.getMeta().getVersion(), Main.getMeta().getAbbrev()));
                    if (isWebViewSupported()) {
                        JFXWindowController controller = MainJavaFx.INSTANCE.getController();
                        Tab webuiTab = JavaFxWebViewWrapper.installWebViewTab(controller.getTabPane(), tlUI(Lang.GUI_MENU_WEBUI), Main.getServer().getWebUiUrl(), Collections.emptyMap(), null);
                        javafx.scene.control.SingleSelectionModel<Tab> selectionModel = controller.getTabPane().getSelectionModel();
                        selectionModel.select(webuiTab);
                        log.info(tlUI(Lang.WEBVIEW_ENABLED));
//                        if (System.getProperty("pbh.enableDownloadWebView") != null) {
//                            for (Downloader downloader : Main.getServer().getDownloaders()) {
//                                if (!downloader.isSupportWebview()) {
//                                    continue;
//                                }
//                                DownloaderBasicAuth basicAuth = downloader.getDownloaderBasicAuth();
//                                Map<String, String> headers = new HashMap<>();
//                                if (basicAuth != null) {
//                                    String cred = Base64.getEncoder().encodeToString((basicAuth.username() + ":" + basicAuth.password()).getBytes(StandardCharsets.UTF_8));
//                                    headers.put("Authorization", "Basic " + cred);
//                                }
//                                JavaFxWebViewWrapper.installWebViewTab(controller.getTabPane(),
//                                        downloader.getName(),
//                                        downloader.getWebUIEndpoint(),
//                                        headers, downloader.getWebViewJavaScript());
//                            }
//                        }
                    } else {
                        log.info(tlUI(Lang.WEBVIEW_DISABLED_WEBKIT_NOT_INCLUDED));
                    }
                });
            }
        });
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
        st.getScene().getRoot().getStylesheets().add(Main.class.getResource("/javafx/css/root.css").toExternalForm());
        st.getScene().getWindow().addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, this::closeWindowEvent);
        JFXWindowController controller = MainJavaFx.INSTANCE.getController();
        this.logsView = controller.getLogsListView();
        this.logsView.setStyle("-fx-font-family: Consolas, Monospace");
        this.logsView.setItems(FXCollections.observableList(new CircularArrayList<>(SwingLoggerAppender.maxLinesSetting + 1)));
        this.logsView.getItems().addListener((InvalidationListener) observable -> {
            if (!logsView.getItems().isEmpty()) {
                logsView.scrollTo(logsView.getItems().size() - 1);
            }
        });
        Holder<Object> lastCell = new Holder<>();
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
        controller.getMenuProgramOpenInGithub().setOnAction(e -> openWebpage(URI.create(Lang.GITHUB_PAGE)));
        controller.getMenuProgramOpenInBrowser().setText(tlUI(Lang.GUI_MENU_WEBUI_OPEN));
        controller.getMenuProgramOpenInBrowser().setOnAction(e -> openWebpage(URI.create(Main.getServer().getWebUiUrl())));
        controller.getMenuProgramCopyWebuiToken().setText(tlUI(Lang.GUI_COPY_WEBUI_TOKEN));
        controller.getMenuProgramCopyWebuiToken().setOnAction(e -> {
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
        if (silentStart) {
            setVisible(false);
        }
    }

    private void closeWindowEvent(WindowEvent windowEvent) {
        minimizeToTray();
    }

    private void minimizeToTray() {
        if (trayIcon != null) {
            setVisible(false);
            trayIcon.displayMessage(tlUI(Lang.GUI_TRAY_MESSAGE_CAPTION), tlUI(Lang.GUI_TRAY_MESSAGE_DESCRIPTION), TrayIcon.MessageType.INFO);
        }
    }


    private void initLoggerRedirection() {
        SwingLoggerAppender.registerListener(loggerEvent -> {
            try {
                Platform.runLater(() -> logsView.getItems().add(new ListLogEntry(loggerEvent.message(), loggerEvent.level())));
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
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(description);
        alert.show();
    }

    @Override
    public void createNotification(Level level, String title, String description) {
        Alert.AlertType alertType = Alert.AlertType.NONE;
        if (level.equals(Level.WARNING)) {
            alertType = Alert.AlertType.WARNING;
        }
        if (level.equals(Level.SEVERE)) {
            alertType = Alert.AlertType.ERROR;
        }
        if (level.equals(Level.INFO)) {
            alertType = Alert.AlertType.INFORMATION;
        }
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(description);
        alert.show();
    }
}
