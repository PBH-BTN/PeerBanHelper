package com.ghostchu.peerbanhelper.platform.impl.posix;

import com.ghostchu.peerbanhelper.platform.Platform;
import com.ghostchu.peerbanhelper.platform.types.EcoQosAPI;
import com.ghostchu.peerbanhelper.platform.types.MalwareScanner;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;


@Slf4j
public class PosixPlatform implements Platform {
    @Override
    public @Nullable EcoQosAPI getEcoQosAPI() {
        return null; // Windows-only
    }

    @Override
    public @Nullable MalwareScanner getMalwareScanner() {
        return null; // Windows-only
    }
}


