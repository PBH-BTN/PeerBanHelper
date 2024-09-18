package com.ghostchu.peerbanhelper.firewall;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.firewall.impl.LocalWindowsAdvFirewall;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import inet.ipaddr.IPAddress;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
/**
 * 实验性功能，暂未实装
 */
public class FirewallManager implements Reloadable {
    private final PeerBanHelperServer server;
    private final Set<Firewall> enabledFirewalls = new HashSet<>();

    public FirewallManager(PeerBanHelperServer server) {
        this.server = server;
        reloadConfig();
        Main.getEventBus().register(this);
    }

    public boolean isApplicable(){
        AtomicBoolean anySuccess = new AtomicBoolean();
        enabledFirewalls.forEach(firewall -> {
            try {
                if (firewall.isApplicable()) {
                    anySuccess.set(true);
                }
            } catch (Exception e) {
                log.error("Check firewall isApplicable failed", e);
            }
        });
        return anySuccess.get();
    }

    public boolean ban(IPAddress address) {
        AtomicBoolean anySuccess = new AtomicBoolean();
        enabledFirewalls.forEach(firewall -> {
            try {
                if (firewall.ban(address)) {
                    anySuccess.set(true);
                }
            } catch (Exception e) {
                log.error("Ban IP via firewall {} failed", firewall.getName(), e);
            }
        });
        return anySuccess.get();
    }

    public boolean unban(IPAddress address) {
        AtomicBoolean anySuccess = new AtomicBoolean();
        enabledFirewalls.forEach(firewall -> {
            try {
                if (firewall.unban(address)) {
                    anySuccess.set(true);
                }
            } catch (Exception e) {
                log.error("Unban IP via firewall {} failed", firewall.getName(), e);
            }
        });
        return anySuccess.get();
    }

    public boolean reset() {
        AtomicBoolean anySuccess = new AtomicBoolean();
        enabledFirewalls.forEach(firewall -> {
            try {
                if (firewall.reset()) {
                    anySuccess.set(true);
                }
            } catch (Exception e) {
                log.error("Reset firewall {} failed", firewall.getName(), e);
            }
        });
        return anySuccess.get();
    }

    public boolean load() {
        AtomicBoolean anySuccess = new AtomicBoolean();
        enabledFirewalls.forEach(firewall -> {
            try {
                if (firewall.reset()) {
                    anySuccess.set(true);
                }
            } catch (Exception e) {
                log.error("Load firewall {} failed", firewall.getName(), e);
            }
        });
        return anySuccess.get();
    }

    public boolean unload() {
        AtomicBoolean anySuccess = new AtomicBoolean();
        enabledFirewalls.forEach(firewall -> {
            try {
                if (firewall.reset()) {
                    anySuccess.set(true);
                }
            } catch (Exception e) {
                log.error("Unload firewall {} failed", firewall.getName(), e);
            }
        });
        return anySuccess.get();
    }

    private void reloadConfig() {
        unload();
        ConfigurationSection section = server.getMainConfig().getConfigurationSection("firewall-integration");
        if (section == null) return;
        if (false && section.getBoolean("windows-adv-firewall-dynamic-keyword") ) {
            var windowsAdvFirewall = new LocalWindowsAdvFirewall(server);
            if (windowsAdvFirewall.isApplicable()) {
                enabledFirewalls.add(windowsAdvFirewall);
            }
        }
        load();
    }

    @Override
    public ReloadResult reloadModule() throws Exception {
        reloadConfig();
        return Reloadable.super.reloadModule();
    }
}
