package com.ghostchu.peerbanhelper.gui.impl.swing;

import org.cef.browser.CefBrowser;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/**
 * 开发者工具弹窗类
 */
public final class JCEFSwingDevTools extends JDialog {
    private final CefBrowser devTools_;

    public JCEFSwingDevTools(JFrame owner, String title, CefBrowser browser) {
        this(owner, title, browser, null);
    }

    public JCEFSwingDevTools(JFrame owner, String title, CefBrowser browser, Point inspectAt) {
        super(owner, title, false);

        setLayout(new BorderLayout());

        devTools_ = browser.getDevTools(inspectAt);
        add(devTools_.getUIComponent());

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentHidden(ComponentEvent e) {
                dispose();
            }
        });
    }

    @Override
    public void dispose() {
        devTools_.close(true);
        super.dispose();
    }
}