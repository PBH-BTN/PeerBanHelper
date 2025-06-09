package com.ghostchu.peerbanhelper.gui.impl.qt.tabs;

import io.qt.gui.QAction;
import io.qt.widgets.QTabWidget;
import io.qt.widgets.QWidget;

import java.util.List;

/**
 * Qt Tab组件的基类
 */
public abstract class QtTabComponent {

    /**
     * 创建Tab页面
     * @param tabWidget 父标签页控件
     */
    public abstract void createTab(QTabWidget tabWidget);

    /**
     * 获取Tab的标题
     * @return Tab标题
     */
    public abstract String getTabTitle();

    /**
     * 获取Tab的内容组件
     * @return Tab内容组件
     */
    public abstract QWidget getTabContent();

    /**
     * 窗口显示时调用
     */
    public void windowShow() {
        // 默认空实现，子类可以重写
    }

    /**
     * 获取托盘菜单项
     * @return 托盘菜单项列表，如果没有返回null
     */
    public List<QAction> getTrayMenuActions() {
        return null;
    }

    /**
     * 释放资源
     */
    public void dispose() {
        // 默认空实现，子类可以重写
    }
}
