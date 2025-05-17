package com.ghostchu.peerbanhelper.gui.impl.console;

import com.ghostchu.peerbanhelper.gui.ProgressDialog;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class ConsoleProgressDialog implements ProgressDialog {

    private String description;
    private String comment;
    private String title;

    public ConsoleProgressDialog(String title, String description, String buttonText, Runnable buttonEvent, boolean allowCancel) {
        this.title = title;
        this.description = description;
    }

    @Override
    public void updateProgress(float progress) {
        log.info("[{}] {}", title, description);
        if (comment != null && !comment.isBlank()) {
            log.info("({})", comment);
        }
        log.info("> {}%", String.format("%.2f", progress * 100));

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
        this.description = description;
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

    @Override
    public void setComment(String comment) {
        this.comment = comment;
    }
}
