package com.ghostchu.peerbanhelper.gui.impl.console;

import com.ghostchu.peerbanhelper.gui.ProgressDialog;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConsoleProgressDialog implements ProgressDialog {

    private String title;

    public ConsoleProgressDialog(String title, String description, String buttonText, Runnable buttonEvent, boolean allowCancel) {
        this.title = title;
    }

    @Override
    public void updateProgress(float progress) {
        log.info("{}: {}%", title, String.format("%.2f", progress * 100));
    }

    @Override
    public void show() {

    }

    @Override
    public void close() {

    }

    @Override
    public void setTitle(String title) {
        this.title = title;
    }

    @Override
    public void setDescription(String description) {

    }

    @Override
    public void setButtonText(String buttonText) {

    }

    @Override
    public void setButtonEvent(Runnable buttonEvent) {

    }

    @Override
    public void setAllowCancel(boolean allowCancel) {

    }

    @Override
    public void setProgressDisplayIndeterminate(boolean indeterminate) {

    }
}
