package com.ghostchu.peerbanhelper.btn;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.util.rule.MatchResult;
import com.ghostchu.peerbanhelper.util.rule.Rule;
import com.ghostchu.peerbanhelper.util.rule.RuleParser;
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

    public BtnRuleParsed(BtnRule btnRule) {
        this.version = btnRule.getVersion();
        this.ipRules = parseIPRule(btnRule.getIpRules());
        this.portRules = parsePortRule(btnRule.getPortRules());
        this.peerIdRules = parseRule(btnRule.getPeerIdRules());
        this.clientNameRules = parseRule(btnRule.getClientNameRules());
    }

    private Map<String, List<Rule>> parsePortRule(Map<String, List<Integer>> portRules) {
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

                });
            }
            rules.put(k, addresses);
        });
        return rules;
    }

    public Map<String, List<Rule>> parseIPRule(Map<String, List<String>> raw) {
        Map<String, List<Rule>> rules = new HashMap<>();
        raw.forEach((k, v) -> {
            List<Rule> addresses = new ArrayList<>();
            for (String s : v) {
                addresses.add(new BtnRuleIpMatcher(IPAddressUtil.getIPAddress(s)));
            }
            rules.put(k, addresses);
        });
        return rules;
    }

    public Map<String, List<Rule>> parseRule(Map<String, List<String>> raw) {
        Map<String, List<Rule>> rules = new HashMap<>();
        raw.forEach((k, v) -> rules.put(k, RuleParser.parse(v)));
        return rules;
    }

    public static class BtnRuleIpMatcher implements Rule {
        private IPAddress ipAddress;

        public BtnRuleIpMatcher(IPAddress ipAddress) {
            this.ipAddress = ipAddress;
            if (this.ipAddress.isIPv4Convertible()) {
                this.ipAddress = this.ipAddress.toIPv4();
            }
        }

        @Override
        public @NotNull MatchResult match(@NotNull String content) {
            Main.getServer().getHitRateMetric().addQuery(this);
            IPAddress contentAddr = IPAddressUtil.getIPAddress(content);
            if (contentAddr.isIPv4Convertible()) {
                contentAddr = contentAddr.toIPv4();
            }
            MatchResult result = (ipAddress.contains(contentAddr) || ipAddress.equals(contentAddr)) ? MatchResult.TRUE : MatchResult.DEFAULT;
            if (result != MatchResult.DEFAULT) {
                Main.getServer().getHitRateMetric().addHit(this);
            }
            return result;
        }

        @Override
        public Map<String, Object> metadata() {
            return Map.of("ip", this.ipAddress.toString());
        }

        @Override
        public String matcherName() {
            return "BTN-IP";
        }
    }
}
