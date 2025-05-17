package com.ghostchu.peerbanhelper.gui.impl.swt;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.PeerBanHelper;
import com.ghostchu.peerbanhelper.api.event.PBHLookAndFeelNeedReloadEvent;
import com.ghostchu.peerbanhelper.api.exchange.ExchangeMap;
import com.ghostchu.peerbanhelper.gui.ProgressDialog;
import com.ghostchu.peerbanhelper.gui.TaskbarControl;
import com.ghostchu.peerbanhelper.gui.impl.GuiImpl;
import com.ghostchu.peerbanhelper.gui.impl.console.ConsoleGuiImpl;
import com.ghostchu.peerbanhelper.gui.impl.swing.theme.PBHFlatLafTheme;
import com.ghostchu.peerbanhelper.gui.impl.swing.theme.impl.StandardLafTheme;
import com.ghostchu.peerbanhelper.api.text.Lang;
import com.ghostchu.peerbanhelper.common.util.CommonUtil;
import com.ghostchu.peerbanhelper.util.logger.JListAppender;
import com.google.common.eventbus.Subscribe;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.internal.DPIUtil;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.*;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.Arrays;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

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
        swtMainWindow = new SwtMainWindow(this, display);
        swtTaskbarControl = new SwtTaskbarControl(swtMainWindow.shell, display);
        initLoggerRedirection();
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
    public void createYesNoDialog(Level level, String title, String description, @Nullable Runnable yesEvent, @Nullable Runnable noEvent) {
        int style = SWT.YES | SWT.NO;
        if (level == Level.INFO) {
            style |= SWT.ICON_INFORMATION;
        }
        if (level == Level.WARNING) {
            style |= SWT.ICON_WARNING;
        }
        if (level == Level.SEVERE) {
            style |= SWT.ICON_ERROR;
        }

        int finalStyle = style;
        display.asyncExec(() -> {
            if (swtMainWindow != null && !swtMainWindow.shell.isDisposed()) {
                swtMainWindow.shell.forceActive();
            }
            MessageBox messageBox = new MessageBox(swtMainWindow != null ? swtMainWindow.shell : new Shell(display), finalStyle);
            messageBox.setText(title);
            messageBox.setMessage(description);
            int result = messageBox.open();
            if (result == SWT.YES) {
                if (yesEvent != null) yesEvent.run();
            } else if (result == SWT.NO) {
                if (noEvent != null) noEvent.run();
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
    public ProgressDialog createProgressDialog(String title, String description, String buttonText, Runnable buttonEvent, boolean allowCancel) {
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
        Table logTable = swtMainWindow.getLogsTabComponent().getTable();
        AtomicBoolean autoScroll = new AtomicBoolean(true);

        // 监听表格的滚动事件
        logTable.addListener(SWT.MouseVerticalWheel, event -> {
            TableItem[] items = logTable.getItems();
            if (items.length > 0) {
                Rectangle rect = items[items.length - 1].getBounds();
                Rectangle area = logTable.getClientArea();
                autoScroll.set((rect.y + rect.height) <= (area.y + area.height));
            }
        });


        // 日志插入线程
        new Thread(() -> {
            JListAppender.allowWriteLogEntryDeque.set(true);
            while (true) {
                try {
                    var logEntry = JListAppender.logEntryDeque.poll(1, TimeUnit.HOURS);
                    if (logEntry == null) continue;
                    if (display.isDisposed()) return;
                    display.asyncExec(() -> {
                        if (logTable.isDisposed()) return;

                        TableItem item = new TableItem(logTable, SWT.NONE);
                        item.setText(logEntry.toString());
                        switch (logEntry.level()) {
                            case ERROR -> {
                                item.setBackground(new org.eclipse.swt.graphics.Color(255, 204, 187));
                                item.setForeground(new org.eclipse.swt.graphics.Color(0, 0, 0));
                            }
                            case WARN -> {
                                item.setBackground(new org.eclipse.swt.graphics.Color(255, 238, 204));
                                item.setForeground(new org.eclipse.swt.graphics.Color(0, 0, 0));
                            }
                        }
                        // 限制最大元素数量
                        int maxSize = 300;
                        try {
                            maxSize = Integer.parseInt(System.getProperty("pbh.gui.logs.maxSize", "300"));
                        } catch (NumberFormatException e) {
                            log.warn("Invalid pbh.gui.logs.maxSize value", e);
                        }

                        while (logTable.getItemCount() > maxSize) {
                            logTable.remove(0);
                        }

                        // 如果启用了自动滚动，滚动到底部
                        if (autoScroll.get()) {
                            logTable.setTopIndex(logTable.getItemCount() - 1);
                        }
                    });
                } catch (InterruptedException e) {
                    log.warn("Failed to update log table", e);
                }
            }
        }).start();
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
        if (level == Level.WARNING) {
            style = SWT.ICON_WARNING;
        }
        if (level == Level.SEVERE) {
            style = SWT.ICON_ERROR;
        }

        // 请求用户关注
        int finalStyle = style;
        display.asyncExec(() -> {
            if (swtMainWindow != null && !swtMainWindow.shell.isDisposed()) {
                swtMainWindow.shell.forceActive();
            }
            MessageBox messageBox = new MessageBox(swtMainWindow != null ? swtMainWindow.shell : new Shell(display), finalStyle);
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
