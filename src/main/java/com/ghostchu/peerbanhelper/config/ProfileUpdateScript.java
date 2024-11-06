package com.ghostchu.peerbanhelper.config;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.text.Lang;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.InvalidConfigurationException;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public class ProfileUpdateScript {
    private final YamlConfiguration conf;

    public ProfileUpdateScript(YamlConfiguration conf) {
        this.conf = conf;
    }



    @UpdateScript(version = 22)
    public void workaroundForBadWebUI() {
        if(conf.getInt("module.auto-range-ban.ipv6") == 32) { // WebUI bug
            conf.set("module.auto-range-ban.ipv6", 60); // Fix it
        }
    }

    @UpdateScript(version = 21)
    public void dailyTrafficAlert() {
        conf.set("module.active-monitoring.traffic-monitoring.daily", -1);
    }

    @UpdateScript(version = 20)
    public void v4v6Tolerate() {
        conf.set("module.multi-dialing-blocker.tolerate-num-ipv4", 2);
        conf.set("module.multi-dialing-blocker.tolerate-num-ipv6", 5);
        conf.set("module.multi-dialing-blocker.tolerate-num", null);
    }

    @UpdateScript(version = 19)
    public void fastPcbTesting() {
        conf.set("module.progress-cheat-blocker.fast-pcb-test-percentage", 0.1d);
        conf.set("module.progress-cheat-blocker.fast-pcb-test-block-duration", 15000);
    }

    @UpdateScript(version = 18)
    public void banDelayWait() {
        conf.set("module.progress-cheat-blocker.max-wait-duration", 30000);
    }

    @UpdateScript(version = 17)
    public void updateProfiles() {
        List<String> bannedPeerIds = conf.getStringList("module.peer-id-blacklist.banned-peer-id");
        bannedPeerIds.add("{\"method\":\"CONTAINS\",\"content\":\"-rn0.0.0\"}");
        conf.set("module.peer-id-blacklist.banned-peer-id", bannedPeerIds);
        List<String> bannedClientNames = conf.getStringList("module.client-name-blacklist.banned-client-name");
        bannedClientNames.add("{\"method\":\"CONTAINS\",\"content\":\"rain 0.0.0\"}");
        bannedClientNames.add("{\"method\":\"CONTAINS\",\"content\":\"gopeed dev\"}");
        conf.set("module.client-name-blacklist.banned-client-name", bannedClientNames);
        conf.set("module.active-monitoring.enabled", true);
    }

    @UpdateScript(version = 16)
    public void progressCheckerIPv6PrefixLength() {
        var section = conf.getConfigurationSection("module.progress-cheat-blocker");
        if (section.getInt("ipv6-prefix-length") == 128) {
            conf.set("module.progress-cheat-blocker.ipv6-prefix-length", 60);
        }
    }

    @UpdateScript(version = 15)
    public void addCitiesBanningRule() {
        conf.set("module.ip-address-blocker.cities", List.of("示例海南"));
        var section = conf.getConfigurationSection("module.ip-address-blocker-rules.rules");
        for (String key : section.getKeys(false)) {
            var rule = section.getConfigurationSection(key);
            var url = rule.getString("url", "");
            if (url.equals("https://cdn.jsdelivr.net/gh/PBH-BTN/BTN-Collected-Rules@master/combine/all.txt") ||
                url.equals("https://fastly.jsdelivr.net/gh/PBH-BTN/BTN-Collected-Rules@master/combine/all.txt")) {
                rule.set("url", "https://bcr.pbh-btn.ghorg.ghostchu-services.top/combine/all.txt");
            }
        }
    }

    @UpdateScript(version = 14)
    public void activeMonitoringAndNetTypeAndPCBPersist() {
        conf.set("module.active-monitoring.enabled", false);
        conf.set("module.active-monitoring.data-retention-time", 5184000000L);
        conf.set("module.active-monitoring.data-cleanup-interval", 604800000L);

        conf.set("module.ip-address-blocker.net-type.wideband", false);
        conf.set("module.ip-address-blocker.net-type.base-station", false);
        conf.set("module.ip-address-blocker.net-type.government-and-enterprise-line", false);
        conf.set("module.ip-address-blocker.net-type.business-platform", false);
        conf.set("module.ip-address-blocker.net-type.backbone-network", false);
        conf.set("module.ip-address-blocker.net-type.ip-private-network", false);
        conf.set("module.ip-address-blocker.net-type.internet-cafe", false);
        conf.set("module.ip-address-blocker.net-type.iot", false);
        conf.set("module.ip-address-blocker.net-type.datacenter", false);

        conf.set("module.progress-cheat-blocker.enable-persist", true);
        conf.set("module.progress-cheat-blocker.persist-duration", 1209600000);
    }

    @UpdateScript(version = 13)
    public void skip() {
    }

    @UpdateScript(version = 12)
    public void patchProgressCheckBlocker() {
        conf.set("module.progress-cheat-blocker.ban-duration", "default");
    }

    @UpdateScript(version = 11)
    public void reAddXL0019() {
        List<String> bannedPeerIds = conf.getStringList("module.peer-id-blacklist.banned-peer-id");
        bannedPeerIds.removeIf(s -> s.contains("-xl0019"));
        conf.set("module.peer-id-blacklist.banned-peer-id", bannedPeerIds);
        List<String> bannedClientNames = conf.getStringList("module.client-name-blacklist.banned-client-name");
        bannedClientNames.removeIf(s -> s.contains("xunlei 0019") || s.contains("xunlei 0.0.1.9"));
        conf.set("module.client-name-blacklist.banned-client-name", bannedClientNames);
        File scripts = new File(Main.getDataDirectory(), "scripts");
        File thunderCheckScript = new File(scripts, "thunder-check.av");
        if (thunderCheckScript.exists()) {
            thunderCheckScript.delete();
        }
    }

    @UpdateScript(version = 10)
    public void addBanDuration() {
        var module = conf.getConfigurationSection("module");
        if (module != null) {
            for (String key : module.getKeys(false)) {
                var mSec = module.getConfigurationSection(key);
                if (mSec != null) {
                    mSec.set("ban-duration", "default");
                }
            }
        }
    }

    @UpdateScript(version = 9)
    public void updateXmRules() {
        List<String> bannedPeerIds = conf.getStringList("module.peer-id-blacklist.banned-peer-id");
        bannedPeerIds.add("{\"method\":\"STARTS_WITH\",\"content\":\"-xm\"}");
        conf.set("module.peer-id-blacklist.banned-peer-id", bannedPeerIds);
        List<String> bannedClientNames = conf.getStringList("module.client-name-blacklist.banned-client-name");
        bannedClientNames.add("{\"method\":\"STARTS_WITH\",\"content\":\"xm/torrent\"}");
        conf.set("module.client-name-blacklist.banned-client-name", bannedClientNames);
    }

    @UpdateScript(version = 8)
    public void bigUpdate() {
        conf.set("ignore-peers-from-addresses", List.of(
                "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "fc00::/7",
                "fd00::/8", "100.64.0.0/10", "169.254.0.0/16", "127.0.0.0/8", "fe80::/10"));
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.getOptions().setParseComments(true);
        try (var in = Main.class.getResourceAsStream("/profile.yml")) {
            if (in == null) {
                log.error("Failed to upgrade configuration, no resources");
                System.exit(1);
                return;
            }
            String str = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            configuration.loadFromString(str);
            conf.set("module.expression-engine", configuration.get("module.expression-engine"));
        } catch (IOException | InvalidConfigurationException e) {
            log.error("Failed to upgrade configuration", e);
            System.exit(1);
        }

    }

    @UpdateScript(version = 7)
    public void progressCheckerIPPrefixLength() {
        conf.set("module.progress-cheat-blocker.ipv4-prefix-length", 32);
        conf.set("module.progress-cheat-blocker.ipv6-prefix-length", 64);
    }

    @UpdateScript(version = 5)
    public void subModule() {
        conf.set("module.ip-address-blocker-rules.enabled", false);
        conf.set("module.ip-address-blocker-rules.check-interval", 86400000);
        conf.set("module.ip-address-blocker-rules.rules.example-rule.enabled", false);
        conf.set("module.ip-address-blocker-rules.rules.example-rule.name", "Example");
        conf.set("module.ip-address-blocker-rules.rules.example-rule.url", "https://example.com/example.txt");
    }


    @UpdateScript(version = 4)
    public void ipDatabase() {
        conf.set("module.ip-address-blocker.asns", new ArrayList<>());
        conf.set("module.ip-address-blocker.regions", new ArrayList<>());
    }

    @UpdateScript(version = 3)
    public void multiDialingBlocker() {
        conf.set("module.multi-dialing-blocker.enabled", false);
        conf.set("module.multi-dialing-blocker.subnet-mask-length", 24);
        conf.set("module.multi-dialing-blocker.subnet-mask-v6-length", 64);
        conf.set("module.multi-dialing-blocker.tolerate-num", 3);
        conf.set("module.multi-dialing-blocker.cache-lifespan", 86400);
        conf.set("module.multi-dialing-blocker.keep-hunting", true);
        conf.set("module.multi-dialing-blocker.keep-hunting-time", 2592000);
    }

    @UpdateScript(version = 2)
    public void newRuleSyntax() {
        List<String> peerId = conf.getStringList("module.peer-id-blacklist.exclude-peer-id");
        List<String> clientName = conf.getStringList("module.client-name-blacklist.exclude-client-name");
        peerId = convertRuleStringExclude(peerId);
        clientName = convertRuleStringExclude(clientName);
        peerId.addAll(convertRuleString(conf.getStringList("module.peer-id-blacklist.banned-peer-id")));
        clientName.addAll(convertRuleString(conf.getStringList("module.client-name-blacklist.banned-client-name")));

        conf.set("module.peer-id-blacklist.banned-peer-id", peerId);
        conf.set("module.client-name-blacklist.banned-client-name", clientName);
        conf.set("module.peer-id-blacklist.exclude-peer-id", null);
        conf.set("module.client-name-blacklist.exclude-client-name", null);
        conf.set("module.active-probing-removed", conf.get("module.active-probing"));
        conf.set("module.active-probing", null);
    }

    @UpdateScript(version = 1)
    public void addExcludeLists() {
        conf.set("module.peer-id-blacklist.exclude-peer-id", Collections.emptyList());
        conf.set("module.client-name-blacklist.exclude-client-name", Collections.emptyList());
    }

    private List<String> convertRuleStringExclude(List<String> oldRules) {
        List<String> newRules = new ArrayList<>();
        for (String oldRule : oldRules) {
            oldRule = oldRule.toLowerCase(Locale.ROOT);
            String[] ruleExploded = oldRule.split("@", 2);
            if (ruleExploded.length != 2) {
                log.error(tlUI(Lang.ERR_INVALID_RULE_SYNTAX, oldRule));
                continue;
            }
            String matchMethod = ruleExploded[0];
            String ruleBody = ruleExploded[1];
            JsonObject newRuleObj = switch (matchMethod) {
                case "contains" -> {
                    JsonObject object = new JsonObject();
                    object.addProperty("method", "CONTAINS");
                    object.addProperty("content", ruleBody);
                    object.addProperty("hit", "FALSE");
                    yield object;
                }
                case "startswith" -> {
                    JsonObject object = new JsonObject();
                    object.addProperty("method", "STARTS_WITH");
                    object.addProperty("content", ruleBody);
                    object.addProperty("hit", "FALSE");
                    yield object;
                }
                case "endswith" -> {
                    JsonObject object = new JsonObject();
                    object.addProperty("method", "ENDS_WITH");
                    object.addProperty("content", ruleBody);
                    object.addProperty("hit", "FALSE");
                    yield object;
                }
                case "length" -> {
                    JsonObject object = new JsonObject();
                    object.addProperty("method", "LENGTH");
                    object.addProperty("min", Integer.parseInt(ruleBody));
                    object.addProperty("max", Integer.parseInt(ruleBody));
                    object.addProperty("hit", "FALSE");
                    yield object;
                }
                case "equals" -> {
                    JsonObject object = new JsonObject();
                    object.addProperty("method", "EQUALS");
                    object.addProperty("content", ruleBody);
                    object.addProperty("success", "FALSE");
                    yield object;
                }
                case "regex" -> {
                    JsonObject object = new JsonObject();
                    object.addProperty("method", "REGEX");
                    object.addProperty("content", ruleBody);
                    object.addProperty("success", "NEGATIVE");
                    yield object;
                }
                default -> null;
            };
            if (newRuleObj != null) {
                newRules.add(newRuleObj.toString());
            }
        }
        return newRules;
    }

    private List<String> convertRuleString(List<String> oldRules) {
        List<String> newRules = new ArrayList<>();
        for (String oldRule : oldRules) {
            oldRule = oldRule.toLowerCase(Locale.ROOT);
            String[] ruleExploded = oldRule.split("@", 2);
            if (ruleExploded.length != 2) {
                log.error(tlUI(Lang.ERR_INVALID_RULE_SYNTAX, oldRule));
                continue;
            }
            String matchMethod = ruleExploded[0];
            String ruleBody = ruleExploded[1];
            JsonObject newRuleObj = switch (matchMethod) {
                case "contains" -> {
                    JsonObject object = new JsonObject();
                    object.addProperty("method", "CONTAINS");
                    object.addProperty("content", ruleBody);
                    yield object;
                }
                case "startswith" -> {
                    JsonObject object = new JsonObject();
                    object.addProperty("method", "STARTS_WITH");
                    object.addProperty("content", ruleBody);
                    yield object;
                }
                case "endswith" -> {
                    JsonObject object = new JsonObject();
                    object.addProperty("method", "ENDS_WITH");
                    object.addProperty("content", ruleBody);
                    yield object;
                }
                case "length" -> {
                    JsonObject object = new JsonObject();
                    object.addProperty("method", "LENGTH");
                    object.addProperty("min", Integer.parseInt(ruleBody));
                    object.addProperty("max", Integer.parseInt(ruleBody));
                    yield object;
                }
                case "equals" -> {
                    JsonObject object = new JsonObject();
                    object.addProperty("method", "EQUALS");
                    object.addProperty("content", ruleBody);
                    yield object;
                }
                case "regex" -> {
                    JsonObject object = new JsonObject();
                    object.addProperty("method", "REGEX");
                    object.addProperty("content", ruleBody);
                    yield object;
                }
                default -> null;
            };
            if (newRuleObj != null) {
                newRules.add(newRuleObj.toString());
            }
        }
        return newRules;
    }
}
