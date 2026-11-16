package com.ghostchu.peerbanhelper.platform;

import com.ghostchu.peerbanhelper.platform.types.EcoQosAPI;
import com.ghostchu.peerbanhelper.platform.types.MalwareScanner;
import com.ghostchu.peerbanhelper.platform.firewall.Firewall;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface Platform {
    @Nullable EcoQosAPI getEcoQosAPI();

    @Nullable MalwareScanner getMalwareScanner();

    /**
     * Returns the lifecycle-scoped native firewall facade for this platform.
     */
    @NotNull Firewall getFirewall();
}
