package com.ghostchu.peerbanhelper.platform;

import com.ghostchu.peerbanhelper.platform.types.EcoQosAPI;
import org.jetbrains.annotations.NotNull;

public interface Platform {
    @NotNull EcoQosAPI getEcoQosAPI();
}
