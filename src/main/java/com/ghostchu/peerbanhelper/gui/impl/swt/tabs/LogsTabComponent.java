package com.ghostchu.peerbanhelper.gui.impl.swt.tabs;

import com.ghostchu.peerbanhelper.text.Lang;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.*;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

/**
 * 运行日志标签页组件
 */
public class LogsTabComponent implements TabComponent {
    private Table table;
    private TableColumn column;

    @Override
    public TabItem createTab(TabFolder tabFolder) {
        TabItem logsTab = new TabItem(tabFolder, SWT.NONE);
        logsTab.setText(getTabName());

        Composite logsComposite = new Composite(tabFolder, SWT.NONE);
        logsComposite.setLayout(new org.eclipse.swt.layout.GridLayout(1, false));
        logsComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        this.table = new Table(logsComposite, SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI | SWT.V_SCROLL);
        this.column = new TableColumn(table, SWT.NONE);
        column.setWidth(tabFolder.getShell().getClientArea().width);
        table.addListener(SWT.Resize, event -> {
            column.setWidth(table.getClientArea().width);
        });
        table.setHeaderVisible(false);
        table.setLinesVisible(false);
        table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        // 设置表格行高自动调整
        table.addListener(SWT.MeasureItem, event -> {
            event.height = event.gc.getFontMetrics().getHeight(); // 可以根据需要调整行高
        });
        logsTab.setControl(logsComposite);

        return logsTab;
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

    }

    /**
     * 显示窗口
     */
    @Override
    public void windowShow() {

    }

    public Table getTable() {
        return table;
    }

    public TableColumn getColumn() {
        return column;
    }
}
