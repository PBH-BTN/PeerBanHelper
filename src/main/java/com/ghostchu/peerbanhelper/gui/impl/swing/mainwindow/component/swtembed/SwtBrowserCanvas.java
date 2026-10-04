package com.ghostchu.peerbanhelper.gui.impl.swing.mainwindow.component.swtembed;

import com.ghostchu.peerbanhelper.Main;
import io.sentry.Sentry;
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
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.util.concurrent.CountDownLatch;

@Slf4j
public final class SwtBrowserCanvas extends Canvas {
    private final Thread swtEventLoop;
    private Display display;
    private Shell shell;
    private Browser browser;
    private final CountDownLatch countDownLatch = new CountDownLatch(1);
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

    public void initBrowser() throws Exception {
        countDownLatch.await();
        display.syncExec(() -> {
            display.setData("org.eclipse.swt.internal.win32.Edge.useDarkPreferedColorScheme", Main.getGuiManager().isDarkMode());

            this.shell = SWT_AWT.new_Shell(display, this);
            // 设置无边距的 FillLayout，让 Browser 自动铺满 Shell
            org.eclipse.swt.layout.FillLayout layout = new org.eclipse.swt.layout.FillLayout();
            layout.marginWidth = 0;
            layout.marginHeight = 0;
            this.shell.setLayout(layout);

            try {
                this.browser = new Browser(this.shell, SWT.NONE);
                this.browser.setVisible(true);
                this.browser.setUrl(Main.getPbhServerAddress());
                this.browserInitialized = true;

                // 应用正确的大小和位置
                updateBrowserSize();
            } catch (SWTError e) {
                this.browserInitialized = false;
                log.debug("Cannot init SWT Browser", e);
                Sentry.captureException(e);
            }
        });
    }

    public void setUrl(String url) {
        if (browser != null && !browser.isDisposed()) {
            display.asyncExec(() -> browser.setUrl(url));
        }
    }

    private void updateBrowserSize() {
        if (browser != null && !browser.isDisposed() && browserInitialized) {
            // 获取 Canvas 逻辑尺寸
            Dimension canvasSize = this.getSize();
            if (canvasSize.width > 0 && canvasSize.height > 0) {

                // 获取当前屏幕的 DPI 缩放比例
                java.awt.GraphicsConfiguration gc = this.getGraphicsConfiguration();
                double scaleX = 1.0;
                double scaleY = 1.0;
                if (gc != null) {
                    java.awt.geom.AffineTransform transform = gc.getDefaultTransform();
                    scaleX = transform.getScaleX();
                    scaleY = transform.getScaleY();
                }

                // 计算 SWT 原生窗口所需的物理像素尺寸
                int physicalWidth = (int) Math.round(canvasSize.width * scaleX);
                int physicalHeight = (int) Math.round(canvasSize.height * scaleY);

                // 应用到 SWT Shell 和 Browser
                shell.setLocation(0, 0);
                shell.setSize(physicalWidth, physicalHeight);
                browser.setLocation(0, 0);
                browser.setSize(physicalWidth, physicalHeight);
            }
            shell.layout(true, true);
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
