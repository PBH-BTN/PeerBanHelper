package com.ghostchu.peerbanhelper.platform.impl.win32;

import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.platform.Platform;
import com.ghostchu.peerbanhelper.platform.WindowsEcoQosAPI;
import com.ghostchu.peerbanhelper.platform.firewall.Firewall;
import com.ghostchu.peerbanhelper.platform.firewall.FirewallFactory;
import com.ghostchu.peerbanhelper.platform.impl.win32.amsi.AmsiScanner;
import com.ghostchu.peerbanhelper.platform.types.EcoQosAPI;
import com.ghostchu.peerbanhelper.platform.types.MalwareScanner;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

@Slf4j
public class WindowsPlatform implements Platform {
    private final EcoQosAPI ecoQosAPI = new WindowsEcoQosAPI();
    private final Firewall firewall = FirewallFactory.create();

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
    public Firewall getFirewall() {
        return firewall;
    }
}
