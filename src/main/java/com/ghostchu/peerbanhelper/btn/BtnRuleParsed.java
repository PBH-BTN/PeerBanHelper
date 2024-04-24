package com.ghostchu.peerbanhelper.btn;

import com.ghostchu.peerbanhelper.util.rule.BasicRule;
import com.ghostchu.peerbanhelper.util.rule.MatchResult;
import com.ghostchu.peerbanhelper.util.rule.Rule;
import com.ghostchu.peerbanhelper.util.rule.RuleParser;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
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
                addresses.add(new BasicRule() {
                    @Override
                    public @NotNull MatchResult match(@NotNull String content) {
                        return Integer.parseInt(content) == s ? MatchResult.POSITIVE : MatchResult.NEUTRAL;
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
                addresses.add(new BasicRule() {
                    final IPAddress ipAddress = new IPAddressString(s).getAddress();

                    @Override
                    public @NotNull MatchResult match(@NotNull String content) {
                        IPAddress contentAddr = new IPAddressString(content).getAddress();
                        return (ipAddress.contains(contentAddr) || ipAddress.equals(contentAddr)) ? MatchResult.POSITIVE : MatchResult.NEUTRAL;
                    }
                });
            }
            rules.put(k, addresses);
        });
        return rules;
    }

    public Map<String, List<Rule>> parseRule(Map<String, List<String>> raw) {
        Map<String, List<Rule>> rules = new HashMap<>();
        raw.forEach((k, v) -> {
            rules.put(k, RuleParser.parse(v));
        });
        return rules;
    }
}
