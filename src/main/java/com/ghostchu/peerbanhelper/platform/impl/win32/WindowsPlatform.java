package com.ghostchu.peerbanhelper.platform.impl.win32;

import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.platform.Platform;
import com.ghostchu.peerbanhelper.platform.WindowsEcoQosAPI;
import com.ghostchu.peerbanhelper.platform.impl.win32.amsi.AmsiScanner;
import com.ghostchu.peerbanhelper.platform.impl.win32.mtr.WindowsMtrTool;
import com.ghostchu.peerbanhelper.platform.types.EcoQosAPI;
import com.ghostchu.peerbanhelper.platform.types.MalwareScanner;
import com.ghostchu.peerbanhelper.platform.types.MtrTool;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

@Slf4j
public class WindowsPlatform implements Platform {
    private final EcoQosAPI ecoQosAPI = new WindowsEcoQosAPI();

    @Nullable
    private final MtrTool mtrTool;

    public WindowsPlatform() {
        MtrTool tool = null;
        try {
            tool = new WindowsMtrTool();
        } catch (Exception e) {
            log.debug("WindowsMtrTool initialisation failed – MTR not available: {}", e.getMessage());
        }
        this.mtrTool = tool;
    }

    @Override
    public @Nullable EcoQosAPI getEcoQosAPI() {
        if (!ExternalSwitch.parseBoolean("pbh.platform.ecoqos-api", true)) {
            return null;
        }
        return ecoQosAPI;
    }

    @Override
    public @Nullable MalwareScanner getMalwareScanner() {
        if (!ExternalSwitch.parseBoolean("pbh.platform.malware-scanner", true)) {
            return null;
        }
        try {
            return new AmsiScanner("PeerBanHelper");
        } catch (Exception e) {
            log.debug("AMSI Scanner is not available: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public @Nullable MtrTool getMtrTool() {
        if (!ExternalSwitch.parseBoolean("pbh.platform.mtr", true)) {
            return null;
        }
        return mtrTool;
    }
}
