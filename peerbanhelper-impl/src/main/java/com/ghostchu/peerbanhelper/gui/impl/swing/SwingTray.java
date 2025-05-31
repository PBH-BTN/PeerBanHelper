package com.ghostchu.peerbanhelper.gui.impl.swing;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;

public final class SwingTray {

    private final JDialog jDialog;
    private final JPopupMenu jPopupMenu;
    private final TrayIcon trayIcon;

    public SwingTray(TrayIcon trayIcon, Consumer<MouseEvent> clickCallback, Consumer<MouseEvent> rightClickCallback) {
        this.trayIcon = trayIcon;
        this.jDialog = new JDialog();
        jDialog.setUndecorated(true);
        jDialog.setSize(1, 1);
        this.jPopupMenu = new JPopupMenu() {
            @Override
            public void firePopupMenuWillBecomeInvisible() {
                jDialog.setVisible(false);
            }
        };
        trayIcon.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                // 左键单击
                if (e.getButton() == 1) {
                    clickCallback.accept(e);
                    return;
                }
                if (e.getButton() == 3 && e.isPopupTrigger()) {
                    rightClickCallback.accept(e);
                    // 获取屏幕相对位置
                    Point point = MouseInfo.getPointerInfo().getLocation();
                    // 将jDialog设置为鼠标位置
                    jDialog.setLocation(point.x, point.y);
                    // 显示载体
                    jDialog.setVisible(true);
                    var dimension = jPopupMenu.getPreferredSize();
                    jPopupMenu.setSize((int) dimension.getWidth() + 1, (int) dimension.getHeight() + 1);
                    // 在载体的0,0处显示对话框
                    jPopupMenu.show(jDialog, 0, 0);
                }
            }
        });
    }

    public void set(List<JMenuItem> items) {
        jPopupMenu.removeAll();
        items.forEach(ele->{
            if(ele == null) {
                jPopupMenu.addSeparator();
            }else{
                jPopupMenu.add(ele);
            }
        });
    }

    public TrayIcon getTrayIcon() {
        return trayIcon;
    }

    public JDialog getjDialog() {
        return jDialog;
    }

    public JPopupMenu getjPopupMenu() {
        return jPopupMenu;
    }
}
