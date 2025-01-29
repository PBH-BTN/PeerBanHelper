package com.ghostchu.peerbanhelper.firewall;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.firewall.impl.LocalWindowsAdvFirewall;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;

import java.util.HashSet;
import java.util.Set;

public final class FirewallManager {
    private final PeerBanHelperServer server;
    private Set<Firewall> enabledFirewalls = new HashSet<>();

    public FirewallManager(PeerBanHelperServer server) {
        this.server = server;
    }

    private void reloadConfig() {
        ConfigurationSection section = Main.getMainConfig().getConfigurationSection("firewall-integration");
        if (section == null) throw new IllegalArgumentException("The firewall-integration section cannot be null");
        if (section.getBoolean("windows-adv-firewall")) {
            enabledFirewalls.add(new LocalWindowsAdvFirewall(server));
        }
    }
}
