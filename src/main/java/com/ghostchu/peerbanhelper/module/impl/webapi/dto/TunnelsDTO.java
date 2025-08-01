package com.ghostchu.peerbanhelper.module.impl.webapi.dto;

import com.ghostchu.peerbanhelper.downloader.DownloaderBasicInfo;
import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class TunnelsDTO {
    private DownloaderBasicInfo downloader;
    private TunnelInfoDTO tunnel;
}
