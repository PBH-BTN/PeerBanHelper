package com.ghostchu.peerbanhelper.btn;

import com.ghostchu.peerbanhelper.scriptengine.CompiledScript;
import com.ghostchu.peerbanhelper.scriptengine.ScriptEngine;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.util.rule.*;
import com.ghostchu.peerbanhelper.util.rule.matcher.IPMatcher;
import inet.ipaddr.format.util.DualIPv4v6AssociativeTries;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Data
@Slf4j
public class BtnRuleParsed {
    private final ScriptEngine scriptEngine;
    private String version;
    private Map<String, List<Rule>> peerIdRules;
    private Map<String, List<Rule>> clientNameRules;
    private Map<String, IPMatcher> ipRules;
    private Map<String, List<Rule>> portRules;
    private Map<String, CompiledScript> scriptRules;

    public BtnRuleParsed(ScriptEngine scriptEngine, BtnRule btnRule, boolean scriptExecute) {
        this.scriptEngine = scriptEngine;
        this.version = btnRule.getVersion();
        this.ipRules = parseIPRule(btnRule.getIpRules());
        this.portRules = parsePortRule(btnRule.getPortRules());
        this.peerIdRules = parseRule(btnRule.getPeerIdRules());
        this.clientNameRules = parseRule(btnRule.getClientNameRules());
        this.scriptRules = scriptExecute ? compileScripts(btnRule.getScriptRules()) : new HashMap<>();
    }

    private Map<String, CompiledScript> compileScripts(Map<String, String> scriptRules) {
        Map<String, CompiledScript> scripts = new HashMap<>();
        log.info(tlUI(Lang.BTN_RULES_SCRIPT_COMPILING, scriptRules.size()));
        long startAt = System.currentTimeMillis();
        scriptRules.forEach((name, content) -> {
            try {
                var script = scriptEngine.compileScript(null, name, content);
                if (script != null) {
                    scripts.put(name, script);
                }
            } catch (Exception e) {
                log.error("Unable to load BTN script {}", name, e);
            }
        });
        log.info(tlUI(Lang.BTN_RULES_SCRIPT_COMPILED, scripts.size(), System.currentTimeMillis() - startAt));
        return scripts;
    }

    /**
     * Parses port rules from a map of port configurations.
     *
     * This method transforms a map of port numbers into a collection of rules for matching port-based conditions.
     * Each port number is converted into an {@code AbstractMatcher} that can evaluate whether a given content
     * matches the specified port.
     *
     * @param portRules A map where keys represent rule identifiers and values are lists of port numbers to match
     * @return A map of rule identifiers to lists of port matching rules, where each rule can evaluate port conditions
     *
     * @see AbstractMatcher
     * @see MatchResult
     * @see TranslationComponent
     */
    private Map<String, List<Rule>> parsePortRule(Map<String, List<Integer>> portRules) {
        Map<String, List<Rule>> rules = new HashMap<>();
        portRules.forEach((k, v) -> {
            List<Rule> addresses = new ArrayList<>();
            for (int s : v) {
                addresses.add(new AbstractMatcher() {
                    @Override
                    public @NotNull MatchResult match0(@NotNull String content) {
                        boolean hit = Integer.parseInt(content) == s;
                        return hit ? new MatchResult(MatchResultEnum.TRUE, new TranslationComponent(Lang.MATCH_CONDITION_PORT_MATCH)) : new MatchResult(MatchResultEnum.DEFAULT, new TranslationComponent("Port seems OK"));
                    }

                    @Override
                    public String metadata() {
                        return String.valueOf(s);
                    }

                    @Override
                    public TranslationComponent matcherName() {
                        return new TranslationComponent(Lang.BTN_PORT_RULE, version);
                    }

                    @Override
                    public String matcherIdentifier() {
                        return "btn-exception:port";
                    }
                });
            }
            rules.put(k, addresses);
        });
        return rules;
    }

    /**
     * Parses IP rules from a raw map of IP address lists and creates corresponding IP matchers.
     *
     * @param raw A map where keys represent rule identifiers and values are lists of IP address strings
     * @return A map of rule identifiers to IP matchers, each containing parsed IP addresses
     *
     * @throws IllegalArgumentException if an invalid IP address is encountered during parsing
     *
     * @see IPMatcher
     * @see DualIPv4v6AssociativeTries
     */
    public Map<String, IPMatcher> parseIPRule(Map<String, List<String>> raw) {
        Map<String, IPMatcher> rules = new HashMap<>();
        raw.forEach((k, v) -> {
            DualIPv4v6AssociativeTries<String> tries = new DualIPv4v6AssociativeTries<>();
            v.stream().map(IPAddressUtil::getIPAddress).forEach(tries::add);
            rules.put(k,new IPMatcher(version, k, List.of(tries)));
        });
        return rules;
    }

    public Map<String, List<Rule>> parseRule(Map<String, List<String>> raw) {
        Map<String, List<Rule>> rules = new HashMap<>();
        raw.forEach((k, v) -> rules.put(k, RuleParser.parse(v)));
        return rules;
    }

    public long size(){
        // check all categories and all value's collections's size
        return peerIdRules.values().stream().mapToLong(List::size).sum() +
                clientNameRules.values().stream().mapToLong(List::size).sum() +
                ipRules.values().stream().mapToLong(IPMatcher::size).sum() +
                portRules.values().stream().mapToLong(List::size).sum() +
                scriptRules.size();
    }

//    public static class BtnRuleIpMatcher extends IPMatcher {
//
//        private final String version;
//
//        public BtnRuleIpMatcher(String version, String ruleId, String ruleName, List<DualIPv4v6Tries> ruleData) {
//            super(ruleId, ruleName, ruleData);
//            this.version = version;
//        }
//
//        @Override
//        public @NotNull TranslationComponent matcherName() {
//            return new TranslationComponent(Lang.BTN_IP_RULE, version);
//        }
//
//        @Override
//        public String matcherIdentifier() {
//            return "btn-exception:ip";
//        }
//    }
}
