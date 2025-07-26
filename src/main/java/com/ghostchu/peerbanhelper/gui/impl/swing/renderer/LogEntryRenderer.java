package com.ghostchu.peerbanhelper.gui.impl.swing.renderer;

import com.ghostchu.peerbanhelper.util.logger.LogEntry;

import javax.swing.*;
import java.awt.*;
import java.lang.ref.SoftReference;

public class LogEntryRenderer extends JTextArea implements ListCellRenderer<SoftReference<LogEntry>> {
    private static final Color errorBackground = new Color(255, 204, 187);
    private static final Color warnBackground = new Color(255, 238, 204);
    private static final Color debugBackground = new Color(204, 255, 204); // 可选：信息级别的背景色
    private static final String oomLogRemoved = "<out of memory, logs has been recycled>";

    public LogEntryRenderer() {
        setLineWrap(true);       // 启用自动换行
        setWrapStyleWord(true);  // 换行时按单词
        setOpaque(true);         // 设置为不透明
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends SoftReference<LogEntry>> list, SoftReference<LogEntry> entry, int index, boolean isSelected, boolean cellHasFocus) {
        var value = entry.get();
        if (value == null) {
            setText(oomLogRemoved);
            return this;
        }

        setText(value.content().trim()); // 设置单元格内容

        if (isSelected) {
            setBackground(list.getSelectionBackground());
            setForeground(list.getSelectionForeground());
        } else {
            setBackground(list.getBackground());
            setForeground(list.getForeground());
            switch (value.level()) {
                case ERROR -> {
                    setBackground(errorBackground);
                    setForeground(Color.BLACK);
                }
                case WARN -> {
                    setBackground(warnBackground);
                    setForeground(Color.BLACK);
                }
                case DEBUG -> {
                    setBackground(debugBackground);
                    setForeground(Color.BLACK);
                }
            }
        }
        setFont(list.getFont());

        // 动态计算行高，无需设置固定行高
        setSize(list.getWidth(), (int) getPreferredSize().getHeight());  // 设置 JTextArea 的宽度来支持换行
        return this;
    }
}
