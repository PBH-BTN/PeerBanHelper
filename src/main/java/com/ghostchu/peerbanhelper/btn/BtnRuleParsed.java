package com.ghostchu.peerbanhelper.btn;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.util.rule.MatchResult;
import com.ghostchu.peerbanhelper.util.rule.Rule;
import com.ghostchu.peerbanhelper.util.rule.RuleParser;
import com.ghostchu.peerbanhelper.util.rule.matcher.IPMatcher;
import inet.ipaddr.IPAddress;
import lombok.Data;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class BtnRuleParsed {
    private String version;
    private Map<String, List<Rule>> peerIdRules;
    private Map<String, List<Rule>> clientNameRules;
    private Map<String, List<Rule>> ipRules;
    private Map<String, List<Rule>> portRules;

    public BtnRuleParsed(@NotNull BtnRule btnRule) {
        this.version = btnRule.getVersion();
        this.ipRules = parseIPRule(btnRule.getIpRules());
        this.portRules = parsePortRule(btnRule.getPortRules());
        this.peerIdRules = parseRule(btnRule.getPeerIdRules());
        this.clientNameRules = parseRule(btnRule.getClientNameRules());
    }

    @NotNull
    private Map<String, List<Rule>> parsePortRule(@NotNull Map<String, List<Integer>> portRules) {
        Map<String, List<Rule>> rules = new HashMap<>();
        portRules.forEach((k, v) -> {
            List<Rule> addresses = new ArrayList<>();
            for (int s : v) {
                addresses.add(new Rule() {
                    @Override
                    public @NotNull MatchResult match(@NotNull String content) {
                        Main.getServer().getHitRateMetric().addQuery(this);
                        boolean hit = Integer.parseInt(content) == s;
                        if (hit) {
                            Main.getServer().getHitRateMetric().addHit(this);
                        }
                        return hit ? MatchResult.TRUE : MatchResult.DEFAULT;
                    }

                    @Override
                    public Map<String, Object> metadata() {
                        return Map.of("port", s);
                    }

                    @Override
                    public String matcherName() {
                        return "BTN-Port";
                    }

                    @Override
                    public String matcherIdentifier() {
                        return "btn:port";
                    }
                });
            }
            rules.put(k, addresses);
        });
        return rules;
    }

    @NotNull
    public Map<String, List<Rule>> parseIPRule(@NotNull Map<String, List<String>> raw) {
        Map<String, List<Rule>> rules = new HashMap<>();
        raw.forEach((k, v) -> rules.put(k, List.of(new BtnRuleIpMatcher(k, k, v.stream().map(IPAddressUtil::getIPAddress).toList()))));
        return rules;
    }

    @NotNull
    public Map<String, List<Rule>> parseRule(@NotNull Map<String, List<String>> raw) {
        Map<String, List<Rule>> rules = new HashMap<>();
        raw.forEach((k, v) -> rules.put(k, RuleParser.parse(v)));
        return rules;
    }

    public static class BtnRuleIpMatcher extends IPMatcher {

        public BtnRuleIpMatcher(String ruleId, String ruleName, List<IPAddress> ruleData) {
            super(ruleId, ruleName, ruleData);
        }

        @Override
        public @NotNull String matcherName() {
            return "BTN-IP";
        }

        @Override
        public String matcherIdentifier() {
            return "btn:ip";
        }
    }
}
