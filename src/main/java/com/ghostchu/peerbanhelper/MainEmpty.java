package com.ghostchu.peerbanhelper;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

public class MainEmpty {
    public static void main(String[] args) {
        // 什么都不做直接退出
        var display = new Display();
        var shell = new Shell();
        shell.setText("test");
        shell.setSize(512, 512);
        shell.open();
        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
    }
}
