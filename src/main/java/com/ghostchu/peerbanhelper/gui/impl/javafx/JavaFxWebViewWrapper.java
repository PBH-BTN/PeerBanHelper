package com.ghostchu.peerbanhelper.gui.impl.javafx;

import com.ghostchu.peerbanhelper.text.Lang;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.AnchorPane;
import javafx.scene.web.WebView;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JavaFxWebViewWrapper {
    public static Tab installWebViewTab(TabPane tabPane, String webuiPath) {
        WebView webView = new WebView();
        log.info("JavaFx WebView engine: Loading page {}", webuiPath);
        webView.getEngine().load(webuiPath);
        AnchorPane anchorPane = new AnchorPane();
        anchorPane.setMinHeight(0);
        anchorPane.getChildren().add(webView);
        Tab tab = new Tab(Lang.GUI_MENU_WEBUI);
        tab.setContent(anchorPane);
        JFXUtil.jfxNodeFitParent(anchorPane);
        JFXUtil.jfxNodeFitParent(webView);
        tabPane.getTabs().add(tab);
        return tab;
    }
}
