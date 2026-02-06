package com.ghostchu.peerbanhelper.platform;

import com.ghostchu.peerbanhelper.platform.types.EcoQosAPI;
import com.ghostchu.peerbanhelper.platform.types.MalwareScanner;
import org.jetbrains.annotations.Nullable;

public interface Platform {
    @Nullable EcoQosAPI getEcoQosAPI();
    @Nullable MalwareScanner getMalwareScanner();
}
