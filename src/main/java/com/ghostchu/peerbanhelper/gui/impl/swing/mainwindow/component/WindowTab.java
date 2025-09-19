package com.ghostchu.peerbanhelper.gui.impl.swing.mainwindow.component;

import com.ghostchu.peerbanhelper.gui.PBHGuiBridge;

public interface WindowTab {

    void onWindowShow();

    void onWindowHide();

    void onWindowResize();

    void onStarted(PBHGuiBridge bridge);
}
