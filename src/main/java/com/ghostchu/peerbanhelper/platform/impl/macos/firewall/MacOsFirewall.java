package com.ghostchu.peerbanhelper.platform.impl.macos.firewall;

import com.ghostchu.peerbanhelper.platform.firewall.Firewall;
import com.ghostchu.peerbanhelper.platform.firewall.FirewallBackend;
import com.ghostchu.peerbanhelper.platform.firewall.FirewallCapabilities;
import com.ghostchu.peerbanhelper.platform.firewall.FirewallCrashCleanup;
import com.ghostchu.peerbanhelper.platform.firewall.UnsupportedFirewall;

/**
 * macOS firewall capability probe.
 *
 * <p>PF is present on macOS, but its supported deployment model requires a
 * referenced anchor or a signed Network Extension. The application avoids
 * modifying the system PF ruleset or pretending to block traffic without such
 * an integration.</p>
 */
public final class MacOsFirewall {
    private MacOsFirewall() {
    }

    public static Firewall create() {
        PfNative.ProbeResult probe = PfNative.probe();
        String detail;
        if (!probe.deviceAvailable()) {
            detail = probe.detail();
        } else {
            detail = probe.detail()
                    + "; configure a dedicated PF anchor or ship a signed Network Extension before enabling it";
        }
        return new UnsupportedFirewall(new FirewallCapabilities(
                FirewallBackend.PACKET_FILTER,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                FirewallCrashCleanup.NONE,
                detail
        ));
    }
}
