package com.ghostchu.peerbanhelper.gui.impl.swt.tabs;

import com.ghostchu.peerbanhelper.gui.impl.swt.SwtGuiImpl;
import com.ghostchu.peerbanhelper.text.Lang;
import org.eclipse.nebula.widgets.grid.Grid;
import org.eclipse.nebula.widgets.grid.GridColumn;
import org.eclipse.nebula.widgets.grid.GridItem;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.*;
import org.slf4j.event.Level;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

/**
 * 运行日志标签页组件
 */
public class LogsTabComponent implements TabComponent {
    private Grid grid;
    private GridColumn messageColumn;

    private static final Color errorBackground = new org.eclipse.swt.graphics.Color(255, 204, 187);
    private static final Color errorForeground = new org.eclipse.swt.graphics.Color(0, 0, 0);
    private static final Color warnBackground = new org.eclipse.swt.graphics.Color(255, 238, 204);
    private static final Color warnForeground = new org.eclipse.swt.graphics.Color(0, 0, 0);
    private Font logFont;

    @Override
    public TabItem createTab(Display display, TabFolder tabFolder) {
        TabItem logsTab = new TabItem(tabFolder, SWT.NONE);
        logsTab.setText(getTabName());

        Composite logsComposite = new Composite(tabFolder, SWT.NONE);
        logsComposite.setLayout(new org.eclipse.swt.layout.GridLayout(1, false));
        logsComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        // 创建 Nebula Grid 控件替代 Table
        this.grid = new Grid(logsComposite, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
        grid.setHeaderVisible(false);
        grid.setLinesVisible(true);
        grid.setAutoHeight(true);  // 启用自动高度，支持多行文本

        // 设置字体与 Swing 版本保持一致 (14号字体)
        this.logFont = SwtGuiImpl.createSwingCompatibleFont(display, null, -1, 10);
        grid.setFont(logFont);

        // 创建日志消息列
        this.messageColumn = new GridColumn(grid, SWT.NONE);
        messageColumn.setText("Message");
        messageColumn.setWordWrap(true); // 启用文本自动换行
        messageColumn.setWidth(tabFolder.getShell().getClientArea().width);

        grid.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        // 首次设置列宽
        adjustColumnWidth();

        // 更改监听方式 - 同时监听Shell和Grid的大小变化
        Listener resizeListener = event -> adjustColumnWidth();

        // 添加到Shell，在窗口整体大小变化时触发
        tabFolder.getShell().addListener(SWT.Resize, resizeListener);

        // 添加到Grid，在Grid控件大小变化时触发
        grid.addListener(SWT.Resize, resizeListener);

        // 添加到父Composite
        logsComposite.addListener(SWT.Resize, resizeListener);
        logsTab.setControl(logsComposite);
        return logsTab;
    }

    /**
     * 调整列宽以适应Grid控件的当前大小
     */
    private void adjustColumnWidth() {
        if (grid == null || grid.isDisposed() || messageColumn == null || messageColumn.isDisposed()) {
            return;
        }

        // 使用异步执行确保布局已完成
        Display.getDefault().asyncExec(() -> {
            if (grid.isDisposed() || messageColumn.isDisposed()) return;

            // 获取Grid的当前客户区宽度
            int width = grid.getClientArea().width;

            // 考虑垂直滚动条的宽度
            if (width > 0) {
                // 减去一些边距，使内容不会紧贴边缘
                // 垂直滚动条显示时会自动占用一些空间
                int scrollBarWidth = grid.getVerticalBar() != null && grid.getVerticalBar().isVisible() ?
                        grid.getVerticalBar().getSize().x : 0;

                // 应用新的列宽
                messageColumn.setWidth(width);

                // 强制重新布局
                grid.redraw();
            }
        });
    }

    @Override
    public String getTabName() {
        return tlUI(Lang.GUI_TABBED_LOGS);
    }

    /**
     * 隐藏窗口
     */
    @Override
    public void windowHide() {
        // 无操作
    }

    /**
     * 显示窗口
     */
    @Override
    public void windowShow() {
        // 无操作
    }

    /**
     * 获取 Grid 控件
     */
    public Grid getGrid() {
        return grid;
    }

    /**
     * 添加日志条目
     * @param message 日志消息
     * @param level 日志级别
     */
    public void addLogEntry(String message, Level level) {
        if (grid == null || grid.isDisposed()) return;

        GridItem item = new GridItem(grid, SWT.NONE);
        item.setText(0, message.replace("\t", "    "));

        switch (level) {
            case WARN -> {
                item.setBackground(0, warnBackground);
                item.setForeground(0, warnForeground);
            }
            case ERROR -> {
                item.setBackground(0, errorBackground);
                item.setForeground(0, errorForeground);
            }
        }
    }

    public void dispose() {
        if (logFont != null && !logFont.isDisposed()) {
            logFont.dispose();
        }
    }

    /**
     * 滚动到底部
     */
    public void scrollToBottom() {
        if (grid == null || grid.isDisposed() || grid.getItemCount() == 0) return;
        grid.showItem(grid.getItem(grid.getItemCount() - 1));
    }

    /**
     * 限制日志条目数量
     * @param maxSize 最大条目数
     */
    public void limitLogEntries(int maxSize) {
        if (grid == null || grid.isDisposed()) return;

        while (grid.getItemCount() > maxSize) {
            grid.remove(0);
        }
    }

    /**
     * 获取垂直滚动条
     */
    public ScrollBar getVerticalBar() {
        return grid.getVerticalBar();
    }
}