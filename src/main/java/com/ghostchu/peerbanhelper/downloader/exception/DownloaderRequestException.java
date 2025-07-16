package com.ghostchu.peerbanhelper.downloader.exception;

public class DownloaderRequestException extends RuntimeException{
    public DownloaderRequestException(String message) {
        super(message);
    }

    public DownloaderRequestException(String message, Throwable cause) {
        super(message, cause);
    }

    public DownloaderRequestException(Throwable cause) {
        super(cause);
    }
}
