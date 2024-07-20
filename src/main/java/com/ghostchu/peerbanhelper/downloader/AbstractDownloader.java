package com.ghostchu.peerbanhelper.downloader;

import com.ghostchu.peerbanhelper.text.TranslationComponent;

public abstract class AbstractDownloader implements Downloader {
    protected String name;
    private DownloaderLastStatus lastStatus = DownloaderLastStatus.UNKNOWN;
    private TranslationComponent statusMessage;

    public AbstractDownloader(String name) {
        this.name = name;
    }

    @Override
    public DownloaderLastStatus getLastStatus() {
        return lastStatus;
    }

    @Override
    public void setLastStatus(DownloaderLastStatus lastStatus, TranslationComponent statusMessage) {
        this.lastStatus = lastStatus;
        this.statusMessage = statusMessage;
    }

    @Override
    public TranslationComponent getLastStatusMessage() {
        return statusMessage;
    }

    @Override
    public String getName() {
        return name;
    }
}
