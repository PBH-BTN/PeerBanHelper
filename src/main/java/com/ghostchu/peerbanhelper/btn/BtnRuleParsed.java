package com.ghostchu.peerbanhelper.btn;

import com.ghostchu.peerbanhelper.util.rule.Rule;
import com.ghostchu.peerbanhelper.util.rule.RuleParser;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class BtnRuleParsed {
    private String version;
    private Map<String, List<Rule>> peerIdRules;
    private Map<String, List<Rule>> clientNameRules;
    private Map<String, List<IPAddress>> ipRules;
    private Map<String, List<Integer>> portRules;

    public BtnRuleParsed(BtnRule btnRule) {
        this.version = btnRule.getVersion();
        this.ipRules = parseIPRule(btnRule.getIpRules());
        this.portRules = btnRule.getPortRules();
        this.peerIdRules = parseRule(btnRule.getPeerIdRules());
        this.clientNameRules = parseRule(btnRule.getClientNameRules());
    }

    public Map<String, List<IPAddress>> parseIPRule(Map<String, List<String>> raw) {
        Map<String, List<IPAddress>> rules = new HashMap<>();
        raw.forEach((k, v) -> {
            List<IPAddress> addresses = new ArrayList<>();
            for (String s : v) {
                addresses.add(new IPAddressString(s).getAddress());
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
