package com.ghostchu.peerbanhelper.gui.impl.javafx;

import com.ghostchu.peerbanhelper.downloader.WebViewScriptCallback;
import com.ghostchu.peerbanhelper.text.Lang;
import com.sun.javafx.scene.control.ContextMenuContent;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.web.WebView;
import javafx.stage.PopupWindow;
import javafx.stage.Window;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.StringJoiner;

@Slf4j
public class JavaFxWebViewWrapper {
    public static Tab installWebViewTab(TabPane tabPane, String tabName, String webuiPath, Map<String, String> headers, @Nullable WebViewScriptCallback initScript) {
        WebView webView = new WebView();
        StringJoiner joiner = new StringJoiner("\n");
        if (!headers.containsKey("User-Agent")) {
            joiner.add(webView.getEngine().getUserAgent());
        }
        headers.forEach((key, value) -> joiner.add(key + ": " + value));
        webView.getEngine().setUserAgent(joiner.toString());
        //installWebViewEventListeners(webView, initScript);
        AnchorPane anchorPane = new AnchorPane();
        anchorPane.setMinHeight(0);
        anchorPane.getChildren().add(webView);
        Tab tab = new Tab(tabName);
        tab.setContent(anchorPane);
        JFXUtil.jfxNodeFitParent(anchorPane);
        JFXUtil.jfxNodeFitParent(webView);
        tabPane.getTabs().add(tab);
        createContextMenu(webView, webuiPath);
        webView.getEngine().load(webuiPath);
        return tab;
    }

    private static PopupWindow getPopupWindow(WebView webView, String webuiPath) {
        for (Window window : Window.getWindows()) {
            if (window instanceof ContextMenu) {
                if (window.getScene() != null && window.getScene().getRoot() != null) {
                    Parent root = window.getScene().getRoot();
                    if (!root.getChildrenUnmodifiable().isEmpty()) {
                        Node popup = root.getChildrenUnmodifiable().getFirst();
                        if (popup.lookup(".context-menu") != null) {
                            Node bridge = popup.lookup(".context-menu");
                            ContextMenuContent cmc = (ContextMenuContent) ((Parent) bridge).getChildrenUnmodifiable().get(0);
                            ContextMenu contextMenu = new ContextMenu();
                            MenuItem reload = new MenuItem(Lang.WEBVIEW_RELOAD_PAGE);
                            reload.setOnAction(e -> webView.getEngine().reload());
                            MenuItem reset = new MenuItem(Lang.WEBVIEW_RESET_PAGE);
                            reset.setOnAction(e -> webView.getEngine().load(webuiPath));
                            MenuItem back = new MenuItem(Lang.WEBVIEW_BACK);
                            back.setOnAction(e -> webView.getEngine().executeScript("history.back()"));
                            MenuItem forward = new MenuItem(Lang.WEBVIEW_FORWARD);
                            forward.setOnAction(e -> webView.getEngine().executeScript("history.forward()"));
                            contextMenu.getItems().addAll(back, forward, new SeparatorMenuItem(), reset);
                            // add new item:
                            cmc.getItemsContainer().getChildren().add(cmc.new MenuItemContainer(new SeparatorMenuItem()));
                            cmc.getItemsContainer().getChildren().add(cmc.new MenuItemContainer(reload));
                            cmc.getItemsContainer().getChildren().add(cmc.new MenuItemContainer(reset));
                            cmc.getItemsContainer().getChildren().add(cmc.new MenuItemContainer(back));
                            cmc.getItemsContainer().getChildren().add(cmc.new MenuItemContainer(forward));
                            return (PopupWindow) window;
                        }
                    }
                }
                return null;
            }
        }
        return null;
    }


    private static void createContextMenu(WebView webView, String webuiPath) {
        webView.setOnContextMenuRequested(e -> getPopupWindow(webView, webuiPath));
    }

    public static String docToString(Document doc) {
        // XML转字符串
        String xmlStr = "";
        try {
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer t = tf.newTransformer();
            t.setOutputProperty("encoding", "UTF-8");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            t.transform(new DOMSource(doc), new StreamResult(bos));
            xmlStr = bos.toString();
        } catch (TransformerException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return xmlStr;
    }
//
//
//    public static void installWebViewEventListeners(WebView webView, @Nullable WebViewScriptCallback callback) {
//        System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
//        System.setProperty("jdk.http.auth.proxying.disabledSchemes", "");
//        if (callback != null) {
//            webView.getEngine().getLoadWorker().stateProperty().addListener((ov, oldState, newState) -> {
//                if (newState == Worker.State.SUCCEEDED) {
//                    String js = callback.pageLoaded(webView.getEngine().getLocation(), docToString(webView.getEngine().getDocument()));
//                    if (js != null) {
//                        webView.getEngine().executeScript(js);
//                    }
//                }
//            });
//        }
//        webView.getEngine().setOnError(event -> log.warn("[WebView] {}", event.getMessage(), event.getException()));
//
//        Authenticator.setDefault(new Authenticator() {
//            @Override
//            protected PasswordAuthentication getPasswordAuthentication() {
//                String prefix = getRequestingScheme() + "://" + getRequestingHost() + ":" + getRequestingPort();
//                for (Downloader downloader : Main.getServer().getDownloaders()) {
//                    DownloaderBasicAuth dba = downloader.getDownloaderBasicAuth();
//                    if (dba != null) {
//                        if (prefix.startsWith(dba.urlPrefix())) {
//                            return new PasswordAuthentication(dba.username(), dba.password().toCharArray());
//                        }
//                    }
//                }
//                return null;
//            }
//        });
//        webView.getEngine().setOnAlert((WebEvent<String> wEvent) -> {
//            Alert alert = new Alert(Alert.AlertType.INFORMATION);
//            alert.setTitle(Lang.JFX_WEBVIEW_ALERT);
//            alert.setHeaderText(Lang.JFX_WEBVIEW_ALERT);
//            alert.setContentText(wEvent.getData());
//        });
//    }

}
