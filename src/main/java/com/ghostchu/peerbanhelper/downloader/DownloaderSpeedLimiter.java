package com.ghostchu.peerbanhelper.downloader;

/**
 * DownloaderSpeedLimiter
 * @param upload 上传速度，无限制为 <= 0
 * @param download 下载速度，无限制为 <= 0
 */
public record DownloaderSpeedLimiter(long upload, long download) {
     public boolean isUploadUnlimited() {
        return upload <= 0;
     }
     public boolean isDownloadUnlimited() {
        return download <= 0;
     }
}
