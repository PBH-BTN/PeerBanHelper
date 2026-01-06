package com.ghostchu.peerbanhelper.platform.impl.win32;

import com.ghostchu.peerbanhelper.platform.Platform;
import com.ghostchu.peerbanhelper.platform.WindowsEcoQosAPI;
import com.ghostchu.peerbanhelper.platform.types.EcoQosAPI;
import org.jetbrains.annotations.NotNull;

public class WindowsPlatform implements Platform {
    private final EcoQosAPI ecoQosAPI = new WindowsEcoQosAPI();
    @Override
    public @NotNull EcoQosAPI getEcoQosAPI() {
        return ecoQosAPI;
    }
}
