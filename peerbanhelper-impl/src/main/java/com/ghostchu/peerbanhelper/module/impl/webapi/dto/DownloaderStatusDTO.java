package com.ghostchu.peerbanhelper.module.impl.webapi.dto;

import com.ghostchu.peerbanhelper.api.downloader.DownloaderLastStatus;
import com.google.gson.JsonObject;

public record DownloaderStatusDTO(DownloaderLastStatus lastStatus, String lastStatusMessage,
                                  long activeTorrents,
                                  long activePeers, JsonObject config, boolean paused) {

}
