package com.ghostchu.peerbanhelper.gui.impl.swt.tabs;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;

/**
 * Tab 组件接口，定义所有标签页组件必须实现的方法
 */
public interface TabComponent {
    /**
     * 创建 Tab 组件
     *
     * @param display
     * @param tabFolder 父级 TabFolder
     * @return 创建的 TabItem
     */
    TabItem createTab(Display display, TabFolder tabFolder);

    /**
     * 刷新 Tab 组件
     */
    default void refresh() {
    }

    /**
     * 获取 Tab 名称
     * @return Tab 名称
     */
    String getTabName();

    /**
     * 隐藏窗口
     */
    void windowHide();

    /**
     * 显示窗口
     */
    void windowShow();
}
