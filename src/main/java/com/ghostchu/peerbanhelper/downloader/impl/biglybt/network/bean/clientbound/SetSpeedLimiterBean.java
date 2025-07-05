package com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.bean.clientbound;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SetSpeedLimiterBean {
    private long upload;
    private long download;
}
