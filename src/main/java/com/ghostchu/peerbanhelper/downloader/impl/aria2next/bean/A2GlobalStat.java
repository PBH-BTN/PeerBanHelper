package com.ghostchu.peerbanhelper.downloader.impl.aria2next.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class A2GlobalStat {
    private Long downloadSpeed;
    private Long uploadSpeed;
    private Long numActive;
    private Long numWaiting;
    private Long numStopped;
    private Long numStoppedTotal;
}
