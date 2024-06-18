package com.ghostchu.peerbanhelper.gui.impl.javafx;

import javafx.scene.Node;
import javafx.scene.layout.AnchorPane;

public class JFXUtil {
    public static void jfxNodeFitParent(Node _node) {
        AnchorPane.setTopAnchor(_node, 0.0);
        AnchorPane.setRightAnchor(_node, 0.0);
        AnchorPane.setLeftAnchor(_node, 0.0);
        AnchorPane.setBottomAnchor(_node, 0.0);
    }
}
