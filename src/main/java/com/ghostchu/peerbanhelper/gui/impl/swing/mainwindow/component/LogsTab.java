package com.ghostchu.peerbanhelper.gui.impl.swing.mainwindow.component;

import com.ghostchu.peerbanhelper.gui.impl.swing.mainwindow.SwingMainWindow;
import com.ghostchu.peerbanhelper.gui.impl.swing.renderer.LogEntryRenderer;

import javax.swing.*;

public class LogsTab {
    private final SwingMainWindow parent;

    public LogsTab(SwingMainWindow parent) {
        this.parent = parent;
        var loggerTextList = parent.getLoggerTextList();
        loggerTextList.setModel(new DefaultListModel<>());
        loggerTextList.setFont(loggerTextList.getFont().deriveFont(14f));
        loggerTextList.setCellRenderer(new LogEntryRenderer());
        loggerTextList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        loggerTextList.setLayoutOrientation(JList.VERTICAL);
        loggerTextList.setFixedCellHeight(-1);
    }
}
