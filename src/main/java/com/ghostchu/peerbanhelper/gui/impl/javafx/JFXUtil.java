package com.ghostchu.peerbanhelper.gui.impl.javafx;

import com.ghostchu.peerbanhelper.Main;
import javafx.scene.Node;
import javafx.scene.layout.AnchorPane;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;

public class JFXUtil {
    public static void jfxNodeFitParent(Node _node) {
        AnchorPane.setTopAnchor(_node, 0.0);
        AnchorPane.setRightAnchor(_node, 0.0);
        AnchorPane.setLeftAnchor(_node, 0.0);
        AnchorPane.setBottomAnchor(_node, 0.0);
    }

    public static void copyText(String content) {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        if (Main.getServer() != null && Main.getServer().getWebContainer() != null) {
            Transferable ts = new StringSelection(content);
            clipboard.setContents(ts, null);
        }
    }
}
