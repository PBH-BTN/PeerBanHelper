package com.ghostchu.peerbanhelper.config;

import com.ghostchu.peerbanhelper.Main;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

import java.io.File;
import java.util.List;
import java.util.UUID;

@Slf4j
public final class MainConfigUpdateScript {
    private final YamlConfiguration conf;

    public MainConfigUpdateScript(YamlConfiguration conf) {
        this.conf = conf;
        validate();
    }

    private void validate() {
        String installationId = conf.getString("installation-id");
        if (installationId == null || installationId.isBlank()) {
            conf.set("installation-id", UUID.randomUUID().toString());
        }
//        String token = conf.getString("server.token");
//        if (token == null || token.isBlank() || token.length() < 8) {
//            conf.set("server.token", UUID.randomUUID().toString());
//            log.info(tlUI(Lang.TOO_WEAK_TOKEN));
//        }
    }

    @UpdateScript(version = 28)
    public void updateBtnNetworkAddress() {
        List<String> outdatedBtnAddress = List.of(
                "https://btn-prod.ghostchu-services.top/ping/config",
                "https://btn-dev.ghostchu-services.top/ping/config",
                "https://btn-dev2.ghostchu-services.top/ping/config",
                "https://sparkle.ghostchu-services.top/ping/config",
                "http://btn-prod.ghostchu-services.top/ping/config",
                "http://btn-dev.ghostchu-services.top/ping/config",
                "http://btn-dev2.ghostchu-services.top/ping/config",
                "http://sparkle.ghostchu-services.top/ping/config"
        );
        if (outdatedBtnAddress.contains(conf.getString("btn.config-url"))) {
            conf.set("btn.config-url", "https://sparkle.pbh-btn.com/ping/config");
        }
    }

    @UpdateScript(version = 27)
    public void updateVacuum() {
        conf.set("persist.vacuum-interval-days", 60);
    }

    @UpdateScript(version = 26)
    public void pushProvidersSMTPStructUpgrade() {
        var pushNotification = conf.getConfigurationSection("push-notification");
        if (pushNotification == null) return;
        for (String key : pushNotification.getKeys(false)) {
            var single = pushNotification.getConfigurationSection(key);
            if (single == null) continue;
            if(single.getBoolean("enabled", false)){
                pushNotification.set(key, null); // 删除未启用的推送渠道
            }else {
                var type = single.getString("type");
                if ("smtp".equals(type)) {
                    single.set("auth", true);
                    if (single.getBoolean("ssl")) {
                        single.set("encryption", "STARTTLS");
                    } else {
                        single.set("encryption", "NONE");
                    }
                    single.set("ssl", null);
                    single.set("sendPartial", true);
                }
                pushNotification.set(key, single);
            }

            conf.set("push-notification", pushNotification);
        }
    }

    @UpdateScript(version = 25)
    public void pushProvidersCleanup() {
        var pushNotification = conf.getConfigurationSection("push-notification");
        if (pushNotification == null) return;
        for (String key : pushNotification.getKeys(false)) {
            var single = pushNotification.getConfigurationSection(key);
            if (single == null) continue;
            single.set("enabled", null);
            var sendKey = single.get("send-key");
            if (sendKey != null) {
                single.set("sendkey", sendKey);
                single.set("send-key", null);
            }
            var chatId = single.get("chat-id");
            if (chatId != null) {
                single.set("chatid", chatId);
                single.set("chat-id", null);
            }
            pushNotification.set(key, single);
        }
        conf.set("push-notification", pushNotification);
    }

    @UpdateScript(version = 24)
    public void decentralizedConfiguration() {
        conf.set("decentralized.enabled", false);
        conf.set("decentralized.kubo-rpc", "/ip4/127.0.0.1/tcp/5001");
        conf.set("decentralized.features.publish-banlist", 3600000);
    }

    @UpdateScript(version = 23)
    public void btnScriptExecuteSwitch() {
        conf.set("btn.allow-script-execute", false);
    }


    @UpdateScript(version = 22)
    public void miscChanges() {
        conf.set("privacy", null);
    }


    @UpdateScript(version = 21)
    public void addPushProvider(YamlConfiguration bundle) {
        conf.set("push-notification", bundle.get("push-notification"));
    }

    @UpdateScript(version = 19)
    public void telemetryErrorReporting() {
        conf.set("privacy.error-reporting", true);
    }

    @UpdateScript(version = 18)
    public void noMaxmindDownload() {
        conf.set("ip-database.account-id", null);
        conf.set("ip-database.license-key", null);
    }

    @UpdateScript(version = 17)
    public void windowsEcoQoSApi() {
        conf.set("performance.windows-ecoqos-api", true);
    }

    @UpdateScript(version = 16)
    public void fixNonProxyHosts() {
        if (!conf.isSet("proxy.non-proxy-hosts")) {
            conf.set("proxy.non-proxy-hosts", conf.get("proxy.non-proxy-host", "localhost|127.*|192.168.*|10.*|172.16.*|172.17.*|172.18.*|172.19.*|172.20.*|172.21.*|172.22.*|172.23.*|172.24.*|172.25.*|172.26.*|172.27.*|172.28.*|172.29.*|172.30.*|172.31.*"));
        }
        conf.set("proxy.non-proxy-host", null);
    }

    @UpdateScript(version = 15)
    public void pbhPlusKeyConfig() {
        conf.set("pbh-plus-key", "");
    }

    @UpdateScript(version = 14)
    public void proxyServerConfigSectionEnhanced() {
        conf.set("proxy.non-proxy-host", "127.0.0.1|localhost");
    }

    @UpdateScript(version = 13)
    public void proxyServerConfigSection() {
        conf.set("proxy.setting", 0);
        conf.set("proxy.host", "127.0.0.1");
        conf.set("proxy.port", 7890);
    }

    @UpdateScript(version = 12)
    public void externalWebUI() {
        conf.set("server.external-webui", false);
    }

    @UpdateScript(version = 11)
    public void corsSetting() {
        conf.set("server.allow-cors", false);
    }

    @UpdateScript(version = 10)
    public void languageSetting() {
        conf.set("language", "default");
    }

    @UpdateScript(version = 9)
    public void firewallIntegration() {
        conf.set("firewall-integration", null);
        conf.set("firewall-integration.windows-adv-firewall", true);
    }

    @UpdateScript(version = 8)
    public void webToken() {
        conf.set("server.token", UUID.randomUUID().toString());
    }

    @UpdateScript(version = 7)
    public void virtualThreads() {
        conf.set("threads", null);
    }

    @UpdateScript(version = 6)
    public void maxmindIpDatabase() {
        conf.set("ip-database.account-id", "");
        conf.set("ip-database.license-key", "");
        conf.set("ip-database.database-city", "GeoLite2-City");
        conf.set("ip-database.database-asn", "GeoLite2-ASN");
        conf.set("ip-database.auto-update", true);
        File file = new File(Main.getDataDirectory(), "banlist.dump");
        if (file.exists()) {
            file.delete(); // 因格式变动，需要删除旧版本的 banlist 持久化信息
        }
    }

    @UpdateScript(version = 5)
    public void optionForDnsReverseLookup() {
        conf.set("lookup.dns-reverse-lookup", false);
    }

    @UpdateScript(version = 4)
    public void defTurnOffIncrementBans() {
        ConfigurationSection section = conf.getConfigurationSection("client");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection downloader = section.getConfigurationSection(key);
            if (downloader != null) {
                if (downloader.getString("type", "").equalsIgnoreCase("qBittorrent")) {
                    downloader.set("increment-ban", false);
                }
            }
        }
    }


    @UpdateScript(version = 3)
    public void transmissionCustomRPCUrl() {
        ConfigurationSection section = conf.getConfigurationSection("client");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection downloader = section.getConfigurationSection(key);
            if (downloader != null) {
                if (downloader.getString("type", "").equalsIgnoreCase("Transmission")) {
                    downloader.set("rpc-url", "/transmission/rpc");
                }
            }
        }

    }

    @UpdateScript(version = 2)
    public void addPersistBanlist() {
        conf.set("persist.banlist", true);
    }

    @UpdateScript(version = 1)
    public void addIncrementBan() {
        ConfigurationSection section = conf.getConfigurationSection("client");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection downloader = section.getConfigurationSection(key);
            if (downloader != null) {
                if (downloader.getString("type", "").equalsIgnoreCase("qBittorrent")) {
                    downloader.set("increment-ban", true);
                }
            }
        }
    }
}
