package com.ghostchu.peerbanhelper.gui;

public interface ProgressDialog extends AutoCloseable {
    void updateProgress(float progress);

    void show();

    void close();

    void setTitle(String title);

    void setDescription(String description);

    void setButtonText(String buttonText);

    void setButtonEvent(Runnable buttonEvent);

    void setAllowCancel(boolean allowCancel);

    void setProgressDisplayIndeterminate(boolean indeterminate);

    void setComment(String comment);
}
