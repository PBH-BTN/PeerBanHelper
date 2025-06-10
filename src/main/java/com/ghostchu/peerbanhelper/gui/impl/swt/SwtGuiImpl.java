package com.ghostchu.peerbanhelper.gui.impl.swt;

import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.PeerBanHelper;
import com.ghostchu.peerbanhelper.event.PBHLookAndFeelNeedReloadEvent;
import com.ghostchu.peerbanhelper.exchange.ExchangeMap;
import com.ghostchu.peerbanhelper.gui.ProgressDialog;
import com.ghostchu.peerbanhelper.gui.TaskbarControl;
import com.ghostchu.peerbanhelper.gui.impl.GuiImpl;
import com.ghostchu.peerbanhelper.gui.impl.console.ConsoleGuiImpl;
import com.ghostchu.peerbanhelper.gui.impl.swing.theme.PBHFlatLafTheme;
import com.ghostchu.peerbanhelper.gui.impl.swing.theme.impl.StandardLafTheme;
import com.ghostchu.peerbanhelper.gui.impl.swt.tabs.LogsTabComponent;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.CommonUtil;
import com.ghostchu.peerbanhelper.util.logger.JListAppender;
import com.google.common.eventbus.Subscribe;
import com.jthemedetecor.OsThemeDetector;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.internal.DPIUtil;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.ScrollBar;
import org.eclipse.swt.widgets.Shell;
import org.jetbrains.annotations.Nullable;
import org.slf4j.event.Level;

import java.net.URI;
import java.util.Arrays;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Getter
@Slf4j
public final class SwtGuiImpl extends ConsoleGuiImpl implements GuiImpl {
    @Getter
    private final boolean silentStart;
    private SwtMainWindow swtMainWindow;
    @Getter
    private PBHFlatLafTheme pbhFlatLafTheme = new StandardLafTheme();
    @Getter
    private SwtTaskbarControl swtTaskbarControl;
    @Getter
    private Display display;

    public SwtGuiImpl(String[] args) {
        super(args);
        System.setProperty("java.awt.headless", "true");
        DPIUtil.setAutoScaleForMonitorSpecificScaling();
        this.silentStart = Arrays.stream(args).anyMatch(s -> s.equalsIgnoreCase("silent"));
        this.display = new Display();
    }

    private void updateGuiStuff() {
        StringBuilder builder = new StringBuilder();
        builder.append(tlUI(Lang.GUI_TITLE_LOADED, "SWT UI", Main.getMeta().getVersion(), Main.getMeta().getAbbrev()));
        StringJoiner joiner = new StringJoiner("", " [", "]");
        joiner.setEmptyValue("");
        ExchangeMap.GUI_DISPLAY_FLAGS.forEach(flag -> joiner.add(flag.getContent()));
        Display.getDefault().asyncExec(() -> {
            if (swtMainWindow != null) {
                swtMainWindow.shell.setText(builder.append(joiner).toString());
            }
        });

    }

    @Override
    public void setup() {
        super.setup();
        Main.getEventBus().register(this);
        setupSwtDefaultFonts();
        try {
            // 这玩意儿能空指针？
            OsThemeDetector detector = OsThemeDetector.getDetector();
            detector.registerListener(this::updateTheme);
            updateTheme(detector.isDark());
        } catch (Exception ignored) {
        }
        swtMainWindow = new SwtMainWindow(this, display);
        swtTaskbarControl = new SwtTaskbarControl(swtMainWindow.shell, display);
        initLoggerRedirection();
    }

    private void updateTheme(Boolean aBoolean) {
       // OS.setTheme(aBoolean);
    }

    /**
     * 设置 SWT 默认字体
     * 与 Swing 实现保持一致的字体大小
     */
    private void setupSwtDefaultFonts() {
        // 在这里可以设置全局字体默认值
        // SWT 不像 Swing 有 UIManager，但可以为各个组件单独设置
    }

    /**
     * 创建与 Swing 实现一致的字体
     * 
     * @param display  当前显示
     * @param fontName 字体名称
     * @param style    字体样式
     * @param size     字体大小
     * @return SWT 字体对象
     */
    public static Font createSwingCompatibleFont(Display display, String fontName, int style, int size) {
        FontData[] fontData = display.getSystemFont().getFontData();
        for (FontData fd : fontData) {
            if (fontName != null) {
                fd.setName(fontName);
            }
            if (style != -1) {
                fd.setStyle(style);
            }
            if (size != -1) {
                fd.setHeight(size);
            }
        }
        return new Font(display, fontData);
    }

    @Override
    public String getName() {
        return "SWT";
    }

    @Override
    public boolean supportInteractive() {
        return true;
    }

    @Override
    public void createYesNoDialog(Level level, String title, String description, @Nullable Runnable yesEvent,
                                  @Nullable Runnable noEvent) {
        int style = SWT.YES | SWT.NO;
        if (level == Level.INFO) {
            style |= SWT.ICON_INFORMATION;
        }
        if (level == Level.WARN) {
            style |= SWT.ICON_WARNING;
        }
        if (level == Level.ERROR) {
            style |= SWT.ICON_ERROR;
        }

        int finalStyle = style;
        display.asyncExec(() -> {
            if (swtMainWindow != null && !swtMainWindow.shell.isDisposed()) {
                swtMainWindow.shell.forceActive();
            }
            MessageBox messageBox = new MessageBox(swtMainWindow != null ? swtMainWindow.shell : new Shell(display),
                    finalStyle);
            messageBox.setText(title);
            messageBox.setMessage(description);
            int result = messageBox.open();
            if (result == SWT.YES) {
                if (yesEvent != null)
                    yesEvent.run();
            } else if (result == SWT.NO) {
                if (noEvent != null)
                    noEvent.run();
            }
        });
    }

    @Override
    public void onPBHFullyStarted(PeerBanHelper server) {
        CommonUtil.getScheduler().scheduleWithFixedDelay(this::updateGuiStuff, 0, 1, TimeUnit.SECONDS);
        swtMainWindow.getWebUITabComponent().navigate(Main.getServer().getWebUiUrl());
    }

    @Subscribe
    public void needReloadThemes(PBHLookAndFeelNeedReloadEvent event) {

    }

    @Override
    public boolean isGuiAvailable() {
        return true;
    }

    @Override
    public ProgressDialog createProgressDialog(String title, String description, String buttonText,
            Runnable buttonEvent, boolean allowCancel) {
        return new SwtProgressDialog(title, description, buttonText, buttonEvent, allowCancel);
    }

    @Override
    public TaskbarControl taskbarControl() {
        if (swtTaskbarControl != null) {
            return swtTaskbarControl;
        }
        return super.taskbarControl();
    }

    private void initLoggerRedirection() {
        LogsTabComponent logsTabComponent = swtMainWindow.getLogsTabComponent();
        AtomicBoolean autoScroll = new AtomicBoolean(true);

        // 监听Grid的滚动事件
        logsTabComponent.getVerticalBar().addListener(SWT.Selection, event -> {
            ScrollBar scrollBar = (ScrollBar) event.widget;
            autoScroll.set(scrollBar.getSelection() + scrollBar.getThumb() >= scrollBar.getMaximum());
        });

        JListAppender.allowWriteLogEntryDeque.set(true);
        var maxSize = ExternalSwitch.parseInt("pbh.gui.logs.maxSize", 300);
        CommonUtil.getScheduler().scheduleWithFixedDelay(() -> display.asyncExec(() -> {
            while (!JListAppender.logEntryDeque.isEmpty()) {
                if (logsTabComponent.getGrid().isDisposed())
                    return;
                var logEntry = JListAppender.logEntryDeque.poll();
                if (logEntry == null)
                    return;                // 添加日志条目到Grid
                logsTabComponent.addLogEntry(logEntry.content(), logEntry.level());

                // 如果启用了自动滚动，滚动到底部
                if (autoScroll.get()) {
                    logsTabComponent.scrollToBottom();
                }
            }

            // 限制最大元素数量
            logsTabComponent.limitLogEntries(maxSize);
        }), 0, 10, TimeUnit.MILLISECONDS);
    }

    @Override
    public void sync() {
        swtMainWindow.sync();
        super.sync();
    }

    @Override
    public void close() {

    }

    @Override
    public void createDialog(Level level, String title, String description, Runnable clickEvent) {
        int style = SWT.NONE;
        if (level == Level.INFO) {
            style = SWT.ICON_INFORMATION;
        }
        if (level == Level.WARN) {
            style = SWT.ICON_WARNING;
        }
        if (level == Level.ERROR) {
            style = SWT.ICON_ERROR;
        }

        // 请求用户关注
        int finalStyle = style;
        display.asyncExec(() -> {
            if (swtMainWindow != null && !swtMainWindow.shell.isDisposed()) {
                swtMainWindow.shell.forceActive();
            }
            MessageBox messageBox = new MessageBox(swtMainWindow != null ? swtMainWindow.shell : new Shell(display),
                    finalStyle);
            messageBox.setText(title);
            messageBox.setMessage(description);
            messageBox.open();
            clickEvent.run();
        });
    }

    @Override
    public void createNotification(Level level, String title, String description) {
        if (swtMainWindow != null) {
            swtMainWindow.trayManager.createNotification(level, title, description);
        } else {
            super.createNotification(level, title, description);
        }
    }

    public void openWebpage(URI uri) {
        Program.launch(uri.toString());
    }
}
