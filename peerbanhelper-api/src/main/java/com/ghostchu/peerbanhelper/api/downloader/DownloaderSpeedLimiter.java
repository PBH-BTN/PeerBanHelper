package com.ghostchu.peerbanhelper.api.downloader;

/**
 * DownloaderSpeedLimiter
 * @param upload 上传速度，无限制为 <= 0
 * @param download 下载速度，无限制为 <= 0
 */
public record DownloaderSpeedLimiter(long upload, long download) {

}
