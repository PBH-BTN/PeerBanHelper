package com.ghostchu.peerbanhelper.util.traversal.btstun;

import com.ghostchu.peerbanhelper.downloader.Downloader;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class BTStunManager {
    private final Map<Downloader, BTStunInstance> perDownloaderStun = Collections.synchronizedMap(new HashMap<>());

    public BTStunManager() {

    }

}
