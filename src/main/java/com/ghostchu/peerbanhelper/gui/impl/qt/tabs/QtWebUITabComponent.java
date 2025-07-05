//package com.ghostchu.peerbanhelper.gui.impl.qt.tabs;
//
//import com.ghostchu.peerbanhelper.Main;
//import com.ghostchu.peerbanhelper.text.Lang;
//import io.qt.core.QUrl;
//import io.qt.webengine.widgets.QWebEngineView;
//import io.qt.webview.QtWebView;
//import io.qt.widgets.QTabWidget;
//import io.qt.widgets.QVBoxLayout;
//import io.qt.widgets.QWidget;
//import lombok.Getter;
//import lombok.extern.slf4j.Slf4j;
//
//import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;
//
///**
// * Qt版本的WebUI标签页组件
// * 使用QWebEngineView实现Web内容显示
// */
//@Slf4j
//@Getter
//public class QtWebUITabComponent extends QtTabComponent {
//
//    private QWidget tabContent;
//    private QtWebView webEngineView;
//    private String lastUrl = "about:blank";
//
//    @Override
//    public void createTab(QTabWidget tabWidget) {
//        QtWebView
//        tabContent = new QWidget();
//        QVBoxLayout layout = new QVBoxLayout(tabContent);
//
//        // 设置布局边距为0，确保WebView填满整个Tab
//        layout.setContentsMargins(0, 0, 0, 0);
//        layout.setSpacing(0);
//
//        // 创建WebEngineView
//        createWebEngineView();
//
//        if (webEngineView != null) {
//            layout.addWidget(webEngineView);
//        }
//
//        // 添加到Tab控件
//        tabWidget.addTab(tabContent, getTabTitle());
//    }
//
//    @Override
//    public String getTabTitle() {
//        return tlUI(Lang.GUI_TABBED_WEBUI);
//    }
//
//    @Override
//    public QWidget getTabContent() {
//        return tabContent;
//    }
//
//    /**
//     * 窗口隐藏时调用
//     */
//    public void windowHide() {
//        disposeWebEngineView();
//    }
//
//    /**
//     * 窗口显示时调用
//     */
//    @Override
//    public void windowShow() {
//        createWebEngineView();
//    }
//
//    /**
//     * 获取WebEngineView实例
//     */
//    public QWebEngineView getWebEngineView() {
//        return webEngineView;
//    }
//
//    /**
//     * 释放WebEngineView资源
//     */
//    private void disposeWebEngineView() {
//        if (webEngineView != null && !webEngineView.isDisposed()) {
//            webEngineView.dispose();
//            webEngineView = null;
//        }
//    }
//
//    /**
//     * 创建WebEngineView实例
//     */
//    private void createWebEngineView() {
//        if (webEngineView == null || webEngineView.isDisposed()) {
//            try {
//                webEngineView = new Webv();
//                // 加载空白页面
//                navigate("about:blank");
//
//                // 如果tabContent已经存在，需要重新布局
//                if (tabContent != null) {
//                    QVBoxLayout layout = (QVBoxLayout) tabContent.layout();
//                    if (layout != null) {
//                        layout.addWidget(webEngineView);
//                    }
//                }
//            } catch (Exception e) {
//                log.error("Failed to create QWebEngineView", e);
//                webEngineView = null;
//            }
//        }
//    }
//
//    /**
//     * 导航到指定URL
//     *
//     * @param url 要导航到的URL
//     */
//    public void navigate(String url) {
//        lastUrl = url;
//        if (webEngineView != null && !webEngineView.isDisposed()) {
//            try {
//                webEngineView.setUrl(new QUrl(url));
//            } catch (Exception e) {
//                log.error("Failed to navigate to URL: " + url, e);
//            }
//        }
//    }
//
//    /**
//     * 导航到WebUI主页
//     */
//    public void navigateToWebUI() {
//        if (Main.getServer() != null && Main.getServer().getWebContainer() != null) {
//            String url = "http://127.0.0.1:" + Main.getServer().getWebContainer().javalin().port() +
//                        "?token=" + Main.getServer().getWebContainer().getToken();
//            navigate(url);
//        }
//    }
//
//    @Override
//    public void dispose() {
//        disposeWebEngineView();
//    }
//}
