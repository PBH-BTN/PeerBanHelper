package com.ghostchu.peerbanhelper.platform;

import com.ghostchu.peerbanhelper.platform.types.EcoQosAPI;
import com.ghostchu.peerbanhelper.platform.types.MalwareScanner;
import com.ghostchu.peerbanhelper.platform.types.MtrTool;
import org.jetbrains.annotations.Nullable;

public interface Platform {
    @Nullable EcoQosAPI getEcoQosAPI();
    @Nullable MalwareScanner getMalwareScanner();

    /**
     * Returns a platform-specific MTR (My Traceroute) tool, or {@code null} if
     * ICMP tracing is not available on this platform / with the current privileges.
     */
    @Nullable MtrTool getMtrTool();
}
