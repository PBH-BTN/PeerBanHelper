package com.ghostchu.peerbanhelper.gui.impl.swing.mainwindow.component.swtembed;

import com.ghostchu.peerbanhelper.Main;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.awt.SWT_AWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;

@Slf4j
public final class SwtBrowserCanvas extends Canvas {
    private final Thread swtEventLoop;
    private Display display;
    private Shell shell;
    private Browser browser;
    private final CountDownLatch countDownLatch = new CountDownLatch(1);
    private double dpiScaleFactor = 1.0;
    private boolean browserInitialized = false;

    public SwtBrowserCanvas() {
        // 在 JVM 启动时设置 Hi-DPI 支持
        this.setupHiDPISupport();
        this.swtEventLoop = this.createEventLoop();
        this.swtEventLoop.start();

        // 添加组件监听器，确保浏览器尺寸始终正确
        this.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                // 延迟执行，确保布局完成后再调整尺寸
                EventQueue.invokeLater(() -> {
                    if (browserInitialized && display != null && !display.isDisposed()) {
                        display.asyncExec(() -> updateBrowserSize());
                    }
                });
            }
        });
    }

    private void setupHiDPISupport() {
        // SWT Hi-DPI 支持
        System.setProperty("swt.autoScale", "exact");
        System.setProperty("swt.autoScale.method", "nearest");
        // Edge 浏览器，别用 IE
        System.setProperty("org.eclipse.swt.browser.DefaultType", "edge");
    }

    public void initBrowser() throws InterruptedException {
        countDownLatch.await();
        display.syncExec(() -> {
            display.setData("org.eclipse.swt.internal.win32.Edge.useDarkPreferedColorScheme", Main.getGuiManager().isDarkMode());
            // 计算 DPI 缩放比例
            calculateDPIScaleFactor();
            this.shell = SWT_AWT.new_Shell(display, this);
            try {
                this.browser = new Browser(this.shell, SWT.NONE);
                this.browser.setVisible(true);
                try (var input = Main.class.getResourceAsStream("/placeholder.html")) {
                    if (input != null) {
                        this.browser.setText(new String(input.readAllBytes(), StandardCharsets.UTF_8));
                    }
                } catch (Exception e) {
                    log.debug("Cannot load placeholder.html", e);
                }
                this.browserInitialized = true;
                // 应用正确的大小
                updateBrowserSize();
            } catch (SWTError e) {
                this.browserInitialized = false;
                log.debug("Cannot init SWT Browser", e);
            }
        });
    }

    public void setUrl(String url) {
        if (browser != null && !browser.isDisposed()) {
            display.asyncExec(() -> browser.setUrl(url));
        }
    }

    private void calculateDPIScaleFactor() {
        int dpi = display.getDPI().x;
        int standardDPI = 96; // Windows 标准 DPI
        this.dpiScaleFactor = (double) dpi / standardDPI;
    }

    private void updateBrowserSize() {
        if (browser != null && !browser.isDisposed() && browserInitialized) {
            // 获取 Canvas 的实际大小并应用 DPI 缩放
            Dimension canvasSize = this.getSize();
            if (canvasSize.width > 0 && canvasSize.height > 0) {
                int scaledWidth = (int) (canvasSize.width / dpiScaleFactor);
                int scaledHeight = (int) (canvasSize.height / dpiScaleFactor);

                browser.setSize(scaledWidth, scaledHeight);
                shell.setSize(scaledWidth, scaledHeight);

                // 强制重新布局
                shell.layout(true, true);
            }
        }
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        super.setBounds(x, y, width, height);
        // 当 Canvas 大小改变时，同步更新 Browser 大小
        if (browserInitialized && display != null && !display.isDisposed()) {
            display.asyncExec(this::updateBrowserSize);
        }
    }

    @Override
    public void setSize(Dimension d) {
        super.setSize(d);
        // 重写 setSize 方法，确保任何尺寸变化都会触发浏览器尺寸更新
        if (browserInitialized && display != null && !display.isDisposed()) {
            display.asyncExec(this::updateBrowserSize);
        }
    }

    @Override
    public void setSize(int width, int height) {
        super.setSize(width, height);
        // 重写 setSize 方法，确保任何尺寸变化都会触发浏览器尺寸更新
        if (browserInitialized && display != null && !display.isDisposed()) {
            display.asyncExec(this::updateBrowserSize);
        }
    }

    private Thread createEventLoop() {
        var thread = new Thread() {
            @Override
            public void run() {
                display = new Display();

                countDownLatch.countDown();
                while (!isInterrupted()) {
                    if (!display.readAndDispatch()) {
                        display.sleep();
                    }
                }
            }
        };
        thread.setDaemon(true);
        thread.setName("SwtBrowserEventLoop");
        return thread;
    }

    @Override
    public void removeNotify() {
        // 组件被移除时清理资源
        if (display != null && !display.isDisposed()) {
            display.asyncExec(() -> {
                if (browser != null && !browser.isDisposed()) {
                    browser.dispose();
                }
                if (shell != null && !shell.isDisposed()) {
                    shell.dispose();
                }
            });
        }
        swtEventLoop.interrupt();
        super.removeNotify();
    }

    @Override
    public boolean isValid() {
        if (display != null && !display.isDisposed()) {
            if (shell != null && !shell.isDisposed()) {
                return browser != null && !browser.isDisposed();
            }
        }
        return false;
    }

    public void refresh() {
        if (browser != null && !browser.isDisposed()) {
            display.asyncExec(browser::refresh);
        }
    }
}
