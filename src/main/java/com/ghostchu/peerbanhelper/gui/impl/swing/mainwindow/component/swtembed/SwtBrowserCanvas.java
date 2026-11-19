package com.ghostchu.peerbanhelper.gui.impl.swing.mainwindow.component.swtembed;

import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.awt.SWT_AWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.event.ComponentEvent;

@Slf4j
public final class SwtBrowserCanvas extends Canvas {
    private static final int UPDATE_INTERVAL_MILLIS = 50;

    private final boolean darkMode;
    private volatile boolean removed;
    private volatile boolean terminated;
    private volatile String url;
    private volatile Dimension canvasSize = new Dimension();

    public SwtBrowserCanvas(boolean darkMode, String initialUrl) {
        this.darkMode = darkMode;
        this.url = initialUrl;
        System.setProperty("swt.autoScale", "exact");
        System.setProperty("swt.autoScale.method", "nearest");
        System.setProperty("org.eclipse.swt.browser.DefaultType", "edge");
    }

    @Override
    public void addNotify() {
        super.addNotify();
        Thread.ofPlatform().daemon().name("SwtBrowserEventLoop").start(this::runEventLoop);
    }

    @Override
    public void removeNotify() {
        removed = true;
        super.removeNotify();
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        super.setBounds(x, y, width, height);
        canvasSize = new Dimension(width, height);
    }

    @Override
    protected void processComponentEvent(ComponentEvent event) {
        // SWT_AWT.new_Shell installs a resize listener that calls Display.syncExec on the EDT.
        // setBounds publishes the size for the SWT thread to apply instead.
        if (event.getID() != ComponentEvent.COMPONENT_RESIZED) {
            super.processComponentEvent(event);
        }
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public boolean isTerminated() {
        return terminated;
    }

    private void runEventLoop() {
        Display display = null;
        try {
            if (removed) {
                return;
            }
            display = new Display();
            if (removed) {
                return;
            }
            display.setData("org.eclipse.swt.internal.win32.Edge.useDarkPreferedColorScheme", darkMode);
            double dpiScaleFactor = display.getDPI().x / 96.0;
            Shell shell = SWT_AWT.new_Shell(display, this);
            if (removed) {
                return;
            }
            Browser browser = new Browser(shell, SWT.NONE);
            if (removed) {
                return;
            }
            browser.setVisible(true);

            // The timer wakes the SWT loop to read Swing state without any SWT calls on the EDT.
            Display browserDisplay = display;
            Runnable update = new Runnable() {
                private String appliedUrl;
                private Dimension appliedSize;

                @Override
                public void run() {
                    if (removed || shell.isDisposed() || browser.isDisposed()) {
                        return;
                    }
                    Dimension size = canvasSize;
                    if (!size.equals(appliedSize)) {
                        updateBrowserSize(shell, browser, size, dpiScaleFactor);
                        appliedSize = size;
                    }
                    String targetUrl = url;
                    if (targetUrl != null && !targetUrl.equals(appliedUrl)) {
                        browser.setUrl(targetUrl);
                        appliedUrl = targetUrl;
                    }
                    browserDisplay.timerExec(UPDATE_INTERVAL_MILLIS, this);
                }
            };
            update.run();

            while (!removed && !shell.isDisposed() && !browser.isDisposed()) {
                if (!display.readAndDispatch()) {
                    display.sleep();
                }
            }
        } catch (RuntimeException | SWTError | LinkageError e) {
            if (!removed) {
                log.warn("Unable to run embedded SWT browser", e);
                Sentry.captureException(e);
            }
        } finally {
            // Display owns the Shell and Browser and disposes both on the SWT thread.
            try {
                if (display != null && !display.isDisposed()) {
                    display.dispose();
                }
            } finally {
                terminated = true;
            }
        }
    }

    private void updateBrowserSize(Shell shell, Browser browser, Dimension size, double dpiScaleFactor) {
        if (size.width > 0 && size.height > 0) {
            int width = (int) (size.width / dpiScaleFactor);
            int height = (int) (size.height / dpiScaleFactor);
            browser.setSize(width, height);
            shell.setSize(width, height);
            shell.layout(true, true);
        }
    }
}
