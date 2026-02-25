package com.ghostchu.peerbanhelper.config;

import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.util.CommonUtil;
import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.configuration.MemoryConfiguration;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
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

    @UpdateScript(version = 45)
    public void ipv6remapping(YamlConfiguration bundle) {
        int prefixLength = conf.getInt("banlist-remapping.ipv6.remap-range");
        if (prefixLength == 60) {
            conf.set("banlist-remapping.ipv6.remap-range", 52);
        }
    }

    @UpdateScript(version = 44)
    public void availableTestRename(YamlConfiguration bundle) {
        var test = conf.getBoolean("stun.availableTest", bundle.getBoolean("stun.availableTest"));
        conf.set("stun.available-test", test);
        conf.set("stun.availableTest", null);
    }

    @UpdateScript(version = 43)
    public void databaseTypeChange(YamlConfiguration bundle) {
        var id = conf.getInt("database.type", -1);
        if (id == -1) return;
        switch (id) {
            case 1 -> conf.set("database.type", "h2");
            case 2, 10 -> conf.set("database.type", "mysql");
            case 3, 11 -> conf.set("database.type", "postgresql");
            default -> conf.set("database.type", "sqlite"); // 0 and other use sqlite
        }
    }


    @UpdateScript(version = 42)
    public void databaseSection(YamlConfiguration bundle) {
        conf.set("database", bundle.get("database"));
    }

    @UpdateScript(version = 41)
    public void privacyAnalytics(YamlConfiguration bundle) {
        conf.set("privacy.analytics", bundle.get("privacy.analytics"));
    }

    @UpdateScript(version = 40)
    public void banlistRemapping(YamlConfiguration bundle) {
        conf.set("banlist-remapping", bundle.get("banlist-remapping"));
    }

    @UpdateScript(version = 38)
    public void pbhKeyString2List() {
        var key = conf.getString("pbh-plus-key");
        List<String> keys = new ArrayList<>();
        if (key != null && !key.isBlank()) {
            keys.add(key);
        }
        conf.set("pbh-plus-key", keys);
    }

    @UpdateScript(version = 37)
    public void synologyNetworkModeChangeTweaks() {
        if (!ExternalSwitch.parseBoolean("pbh.pkg.synology", false)) return;
        var client = conf.getConfigurationSection("client");
        if (client == null) return;
        for (String key : client.getKeys(false)) {
            var downloaderSection = client.getConfigurationSection(key);
            if (downloaderSection == null) continue;
            var endpoint = downloaderSection.getString("endpoint");
            if (endpoint == null) continue;
            var uri = URI.create(endpoint);
            var host = uri.getHost();
            if (host.startsWith("172.")) {
                try {
                    log.info("Updating endpoint for downloader {} for Synology DSM package upgrade from {} to {}.", key, endpoint, downloaderSection.getString("endpoint"));
                    downloaderSection.set("endpoint", (new URI(uri.getScheme(), null, "127.0.0.1", uri.getPort(), uri.getPath(), null, null)).toString());
                } catch (URISyntaxException e) {
                    log.error("Unable to update endpoint for downloader {} on Synology DSM: {}", key, e.getMessage());
                    Sentry.captureException(e);
                }
            }
        }
    }

    @UpdateScript(version = 36)
    public void stunSettings(YamlConfiguration bundle) {
        conf.set("stun", bundle.getConfigurationSection("stun"));
        conf.set("auto-stun", bundle.getConfigurationSection("auto-stun"));
    }

    @UpdateScript(version = 35)
    public void migrateProxySettings() {
        switch (conf.getInt("proxy.setting", 0)) { // 旧版本不代理，现在调整为不代理
            case 0 -> {
            } // 旧版本是不代理，现在也不代理
            case 1 -> conf.set("proxy.setting", 0); // 旧版本是跟随系统代理，现在调整为不代理
            case 2 -> conf.set("proxy.setting", 1); // HTTP 代理保持现状
            case 3 -> conf.set("proxy.setting", 2); // SOCKS 代理保持现状
        }
    }

    @UpdateScript(version = 34)
    public void cleanupUnusedFiles() {
        File decentralized = new File(Main.getDataDirectory(), "decentralized");
        CommonUtil.deleteFileOrDirectory(decentralized);
        File ipfs = new File(Main.getDataDirectory(), "ipfs");
        CommonUtil.deleteFileOrDirectory(ipfs);
        File p2p = new File(Main.getDataDirectory(), "p2p");
        CommonUtil.deleteFileOrDirectory(p2p);
    }

    @UpdateScript(version = 33)
    public void cleanupDownloadedJCEFComponents() {
        File jcefDirectory = new File(Main.getDataDirectory(), "jcef");
        CommonUtil.deleteFileOrDirectory(jcefDirectory);
    }

    @UpdateScript(version = 32)
    public void assignUniqueIdForDownloader() {
        var clients = conf.getConfigurationSection("client");
        if (clients == null) return;
        var newClients = new MemoryConfiguration();
        for (String name : clients.getKeys(false)) {
            String uuid = UUID.randomUUID().toString();
            ConfigurationSection oldSection = clients.getConfigurationSection(name);
            if (oldSection == null) continue;
            oldSection.set("name", name);
            newClients.set(uuid, oldSection);
            log.info("[Downloader Id Re-assign] {} -> {}", name, uuid);
            ConfigTransfer.downloaderNameToUUID.put(name, uuid);
        }
        conf.set("client", newClients);
    }

    @UpdateScript(version = 31)
    public void removeBanInvoker() {
        conf.set("banlist-invoker", null);
    }

    @UpdateScript(version = 30)
    public void addGuiSettings() {
        conf.set("gui", "auto");
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
            if (single.getBoolean("enabled", false)) {
                pushNotification.set(key, null); // 删除未启用的推送渠道
            } else {
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
                if ("qBittorrent".equalsIgnoreCase(downloader.getString("type", ""))) {
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
                if ("Transmission".equalsIgnoreCase(downloader.getString("type", ""))) {
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
                if ("qBittorrent".equalsIgnoreCase(downloader.getString("type", ""))) {
                    downloader.set("increment-ban", true);
                }
            }
        }
    }
}
