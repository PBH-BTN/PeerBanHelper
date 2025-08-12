package com.ghostchu.peerbanhelper.gui.impl.swing.mainwindow.component;

import com.ghostchu.peerbanhelper.gui.PBHGuiBridge;
import com.ghostchu.peerbanhelper.gui.impl.swing.mainwindow.SwingMainWindow;
import com.ghostchu.peerbanhelper.gui.impl.swing.renderer.LogEntryRenderer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.logger.LogEntry;
import lombok.Getter;

import javax.swing.*;
import java.awt.*;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

public class LogsTab implements WindowTab {
    private final SwingMainWindow parent;
    private JPanel tabbedPaneLogs;
    @Getter
    private JList<LogEntry> loggerTextList;

    @Getter
    private JScrollPane loggerScrollPane;

    public LogsTab(SwingMainWindow parent) {
        this.parent = parent;
        loggerScrollPane = new JScrollPane();
        tabbedPaneLogs = new JPanel();
        tabbedPaneLogs.setLayout(new BorderLayout(0, 0));
        parent.getTabbedPane().addTab("Logs", tabbedPaneLogs);
        tabbedPaneLogs.add(loggerScrollPane, BorderLayout.CENTER);
        loggerTextList = new JList<>();
        Font loggerTextListFont = UIManager.getFont("TextArea.font");
        if (loggerTextListFont != null) loggerTextList.setFont(loggerTextListFont);
        loggerScrollPane.setViewportView(loggerTextList);
        SwingMainWindow.setTabTitle(tabbedPaneLogs, tlUI(Lang.GUI_TABBED_LOGS));

        loggerScrollPane.setEnabled(true);
        Font loggerScrollPaneFont = parent.getFont(null, -1, -1, loggerScrollPane.getFont());
        if (loggerScrollPaneFont != null) loggerScrollPane.setFont(loggerScrollPaneFont);
        loggerScrollPane.setVerticalScrollBarPolicy(22);
        loggerTextList.setModel(new DefaultListModel<>());
        loggerTextList.setFont(loggerTextList.getFont().deriveFont(14f));
        loggerTextList.setCellRenderer(new LogEntryRenderer());
        loggerTextList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        loggerTextList.setLayoutOrientation(JList.VERTICAL);
        loggerTextList.setFixedCellHeight(-1);
    }

    @Override
    public void onWindowShow() {

    }

    @Override
    public void onWindowHide() {

    }

    @Override
    public void onWindowResize() {

    }

    @Override
    public void onStarted(PBHGuiBridge bridge) {

    }
}
