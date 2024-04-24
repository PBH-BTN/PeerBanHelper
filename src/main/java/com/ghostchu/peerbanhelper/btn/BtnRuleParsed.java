package com.ghostchu.peerbanhelper.btn;

import com.ghostchu.peerbanhelper.util.rule.Rule;
import com.ghostchu.peerbanhelper.util.rule.RuleParser;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class BtnRuleParsed {
    private String version;
    private Map<String, List<Rule>> peerIdRules;
    private Map<String, List<Rule>> clientNameRules;
    private Map<String, List<String>> ipRules;
    private Map<String, List<Integer>> portRules;

    public BtnRuleParsed(BtnRule btnRule) {
        this.version = btnRule.getVersion();
        this.ipRules = btnRule.getIpRules();
        this.portRules = btnRule.getPortRules();
        this.peerIdRules = parseRule(btnRule.getPeerIdRules());
        this.clientNameRules = parseRule(btnRule.getClientNameRules());
    }

    public Map<String, List<Rule>> parseRule(Map<String, List<String>> raw) {
        Map<String, List<Rule>> rules = new HashMap<>();
        raw.forEach((k, v) -> {
            rules.put(k, RuleParser.parse(v));
        });
        return rules;
    }
}
