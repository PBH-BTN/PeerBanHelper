package com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.bean.clientbound;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class PeerThrottlingBean {
    private int uploadRate;
    private int downloadRate;
}
