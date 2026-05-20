package com.ghostchu.peerbanhelper.platform.impl.posix;

import com.ghostchu.peerbanhelper.platform.Platform;
import com.ghostchu.peerbanhelper.platform.impl.posix.mtr.PosixMtrTool;
import com.ghostchu.peerbanhelper.platform.types.EcoQosAPI;
import com.ghostchu.peerbanhelper.platform.types.MalwareScanner;
import com.ghostchu.peerbanhelper.platform.types.MtrTool;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

/**
 * {@link Platform} implementation for Linux and macOS.
 *
 * <p>Currently provides:
 * <ul>
 *   <li>{@link MtrTool} via {@link PosixMtrTool} (ICMP, with automatic
 *       privilege-level detection)</li>
 *   <li>EcoQosAPI: not available (Windows-only)</li>
 *   <li>MalwareScanner: not available (Windows-only)</li>
 * </ul>
 */
@Slf4j
public class PosixPlatform implements Platform {

    @Nullable
    private final MtrTool mtrTool;

    public PosixPlatform() {
        MtrTool tool = null;
        try {
            tool = new PosixMtrTool();
        } catch (Exception e) {
            log.debug("PosixMtrTool initialisation failed – MTR not available: {}", e.getMessage());
        }
        this.mtrTool = tool;
    }

    @Override
    public @Nullable EcoQosAPI getEcoQosAPI() {
        return null; // Windows-only
    }

    @Override
    public @Nullable MalwareScanner getMalwareScanner() {
        return null; // Windows-only
    }

    @Override
    public @Nullable MtrTool getMtrTool() {
        return mtrTool;
    }
}


