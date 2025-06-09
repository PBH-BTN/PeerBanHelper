package com.ghostchu.peerbanhelper.gui.impl.qt.tabs;

import com.ghostchu.peerbanhelper.text.Lang;
import io.qt.core.QPoint;
import io.qt.core.QTimer;
import io.qt.core.Qt;
import io.qt.gui.*;
import io.qt.widgets.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

/**
 * Qt版本的日志标签页组件
 * 使用QTableWidget实现多行文本支持，类似于Swing/SWT实现
 */
@Slf4j
@Getter
public class QtLogsTabComponent extends QtTabComponent {

    private QWidget tabContent;
    private QTableWidget logTable;
    private QScrollBar verticalScrollBar;
    private static final QColor ERROR_BACKGROUND = new QColor(255, 204, 187);
    private static final QColor ERROR_FOREGROUND = new QColor(0, 0, 0);
    private static final QColor WARN_BACKGROUND = new QColor(255, 238, 204);
    private static final QColor WARN_FOREGROUND = new QColor(0, 0, 0);

    @Override
    public void createTab(QTabWidget tabWidget) {
        tabContent = new QWidget();
        QVBoxLayout layout = new QVBoxLayout(tabContent);

        // 创建表格控件替代文本编辑器
        logTable = new QTableWidget(0, 1); // 0行，1列
        logTable.setHorizontalHeaderLabels(java.util.List.of("Message"));
        logTable.horizontalHeader().setVisible(false); // 隐藏表头
        logTable.horizontalHeader().setStretchLastSection(true); // 让消息列自动拉伸
        logTable.verticalHeader().setVisible(false); // 隐藏行号
        logTable.setSelectionBehavior(QAbstractItemView.SelectionBehavior.SelectRows);
        logTable.setSelectionMode(QAbstractItemView.SelectionMode.SingleSelection); // 单行选择
        logTable.setAlternatingRowColors(true);
        logTable.setShowGrid(false);
        logTable.setWordWrap(true); // 启用文字换行

        // 设置字体 - 与Swing实现保持一致的14pt字体
        QFont font = new QFont("Consolas", 10);
        logTable.setFont(font);

        // 创建右键菜单
        createContextMenu();

        // 获取垂直滚动条
        verticalScrollBar = logTable.verticalScrollBar();

        layout.addWidget(logTable);

        // 添加到Tab控件
        tabWidget.addTab(tabContent, getTabTitle());
    }

    private void createContextMenu() {
        QMenu contextMenu = new QMenu(logTable);

        // 清空日志
        QAction clearAction = new QAction("清空日志");
        clearAction.triggered.connect(() -> {
            if (logTable != null) {
                logTable.setRowCount(0);
            }
        });
        contextMenu.addAction(clearAction);

        // 分隔符
        contextMenu.addSeparator();

        // 复制选中行内容
        QAction copyAction = new QAction("复制选中行内容");
        copyAction.triggered.connect(this::copySelectedRowContent);
        contextMenu.addAction(copyAction);

        // 设置上下文菜单
        logTable.setContextMenuPolicy(Qt.ContextMenuPolicy.CustomContextMenu);
        logTable.customContextMenuRequested.connect((QPoint pos) -> {
            // 更新菜单项状态
            boolean hasSelection = logTable.currentRow() >= 0;
            copyAction.setEnabled(hasSelection);

            // 显示菜单
            contextMenu.exec(logTable.mapToGlobal(pos));
        });
    }

    private void copySelectedRowContent() {
        int currentRow = logTable.currentRow();
        if (currentRow >= 0 && currentRow < logTable.rowCount()) {
            QTableWidgetItem item = logTable.item(currentRow, 0);
            if (item != null) {
                String content = item.text();

                // 使用Qt的剪贴板
                QClipboard clipboard = QApplication.clipboard();
                clipboard.setText(content);
            }
        }
    }

    public String getTabTitle() {
        return tlUI(Lang.GUI_TABBED_LOGS);
    }

    @Override
    public QWidget getTabContent() {
        return tabContent;
    }

    /**
     * 添加日志条目
     *
     * @param logMessage 日志消息
     * @param level      日志级别
     */
    public void addLogEntry(String logMessage, Level level) {
        if (logTable == null)
            return;

        // 直接在主线程中处理，因为Qt的UI更新必须在主线程中进行
        try {
            int row = logTable.rowCount();
            logTable.insertRow(row);

            // 创建表格项
            QTableWidgetItem item = new QTableWidgetItem(logMessage);

            // 根据日志级别设置背景和前景色
            if (level == Level.ERROR) {
                item.setBackground(new QBrush(ERROR_BACKGROUND));
                item.setForeground(new QBrush(ERROR_FOREGROUND));
            } else if (level == Level.WARN) {
                item.setBackground(new QBrush(WARN_BACKGROUND));
                item.setForeground(new QBrush(WARN_FOREGROUND));
            }

            // 设置为只读并且支持文本换行
            item.setFlags(new Qt.ItemFlags(Qt.ItemFlag.ItemIsSelectable, Qt.ItemFlag.ItemIsEnabled));

            logTable.setItem(row, 0, item);

            // 调整行高以适应内容
            logTable.resizeRowToContents(row);

            // 强制更新表格显示
            logTable.update();
            logTable.repaint();

            // 自动滚动到底部
            scrollToBottom();

        } catch (Exception e) {
            log.error("Error adding log entry to Qt logs tab", e);
        }
    }

    /**
     * 滚动到底部
     */
    public void scrollToBottom() {
        if (verticalScrollBar != null) {
            // 使用QTimer.singleShot确保在UI更新后滚动
            QTimer.singleShot(10, () -> {
                verticalScrollBar.setValue(verticalScrollBar.maximum());
            });
        }
    }

    /**
     * 限制日志条目数量
     *
     * @param maxEntries 最大条目数
     */
    public void limitLogEntries(int maxEntries) {
        if (logTable == null)
            return;

        try {
            int currentRows = logTable.rowCount();

            if (currentRows > maxEntries) {
                // 删除前面的行，保留最后的maxEntries行
                int rowsToRemove = currentRows - maxEntries;
                for (int i = 0; i < rowsToRemove; i++) {
                    logTable.removeRow(0); // 总是删除第一行
                }

                // 滚动到底部
                scrollToBottom();
            }
        } catch (Exception e) {
            log.error("Error limiting log entries in Qt logs tab", e);
        }
    }

    @Override
    public void dispose() {
        if (logTable != null) {
            logTable.dispose();
        }
        if (tabContent != null) {
            tabContent.dispose();
        }
        super.dispose();
    }
}
