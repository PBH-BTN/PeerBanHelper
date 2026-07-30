package com.ghostchu.peerbanhelper.downloader.impl.aria2next.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class A2GlobalStat {
    private long downloadSpeed;
    private long uploadSpeed;
    private long numActive;
    private long numWaiting;
    private long numStopped;
    private long numStoppedTotal;
}
