package com.ghostchu.peerbanhelper.platform.firewall;

import com.ghostchu.peerbanhelper.platform.impl.linux.firewall.LinuxFirewall;
import com.ghostchu.peerbanhelper.platform.impl.macos.firewall.MacOsFirewall;
import com.ghostchu.peerbanhelper.platform.impl.win32.firewall.WindowsFirewall;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Selects and activates the native firewall backend for the current OS.
 */
public final class FirewallFactory {
    private FirewallFactory() {
    }

    @NotNull
    public static Firewall create() {
        Firewall firewall;
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (os.startsWith("win")) {
                firewall = WindowsFirewall.create();
            } else if (os.startsWith("linux")) {
                firewall = LinuxFirewall.create();
            } else if (os.startsWith("mac") || os.startsWith("darwin")) {
                firewall = MacOsFirewall.create();
            } else {
                firewall = new UnsupportedFirewall("Native firewall is not supported on " + os);
            }
        } catch (Throwable throwable) {
            firewall = new UnsupportedFirewall(
                    "Unable to initialize the native firewall backend: " + throwable.getMessage()
            );
        }
        return firewall.capabilities().available() ? new FirewallManager(firewall) : firewall;
    }
}
