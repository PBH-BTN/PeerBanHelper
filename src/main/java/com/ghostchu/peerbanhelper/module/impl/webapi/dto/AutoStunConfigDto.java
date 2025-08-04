package com.ghostchu.peerbanhelper.module.impl.webapi.dto;

import com.cdnbye.core.nat.NatType;
import com.ghostchu.peerbanhelper.downloader.DownloaderBasicInfo;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@AllArgsConstructor
@Data
public class AutoStunConfigDto {
    private boolean enabled;
    private boolean useFriendlyLoopbackMapping;
    private List<DownloaderBasicInfo> selectedDownloaders;
    private NatType natType;
}
