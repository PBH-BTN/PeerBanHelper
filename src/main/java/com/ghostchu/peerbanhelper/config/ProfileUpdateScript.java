package com.ghostchu.peerbanhelper.config;

import com.ghostchu.peerbanhelper.text.Lang;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Slf4j
public class ProfileUpdateScript {
    private final YamlConfiguration conf;

    public ProfileUpdateScript(YamlConfiguration conf) {
        this.conf = conf;
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
                log.warn(Lang.ERR_INVALID_RULE_SYNTAX, oldRule);
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
                log.warn(Lang.ERR_INVALID_RULE_SYNTAX, oldRule);
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
