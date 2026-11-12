package com.ghostchu.peerbanhelper.platform.impl.posix;

import com.ghostchu.peerbanhelper.platform.Platform;
import com.ghostchu.peerbanhelper.platform.firewall.Firewall;
import com.ghostchu.peerbanhelper.platform.firewall.UnsupportedFirewall;
import com.ghostchu.peerbanhelper.platform.types.EcoQosAPI;
import com.ghostchu.peerbanhelper.platform.types.MalwareScanner;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;


@Slf4j
public class PosixPlatform implements Platform {
    private final Firewall firewall = new UnsupportedFirewall("Native POSIX firewall backend has not been initialized");

    @Override
    public @Nullable EcoQosAPI getEcoQosAPI() {
        return null; // Windows-only
    }

    @Override
    public @Nullable MalwareScanner getMalwareScanner() {
        return null; // Windows-only
    }

    @Override
    public Firewall getFirewall() {
        return firewall;
    }
}


