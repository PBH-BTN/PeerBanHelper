package com.ghostchu.peerbanhelper.btn;

import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.util.rule.*;
import com.ghostchu.peerbanhelper.util.rule.matcher.IPMatcher;
import inet.ipaddr.format.util.DualIPv4v6AssociativeTries;
import lombok.Data;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class BtnExceptionRuleParsed {
    private String version;
    private Map<String, List<Rule>> peerIdRules;
    private Map<String, List<Rule>> clientNameRules;
    private Map<String, List<Rule>> ipRules;
    private Map<String, List<Rule>> portRules;

    public BtnExceptionRuleParsed(BtnExceptionRule btnRule) {
        this.version = btnRule.getVersion();
        this.ipRules = parseIPRule(btnRule.getIpRules());
        this.portRules = parsePortRule(btnRule.getPortRules());
        this.peerIdRules = parseRule(btnRule.getPeerIdRules());
        this.clientNameRules = parseRule(btnRule.getClientNameRules());
    }

    /**
     * Parses port rules from a map of port configurations and creates matching rules.
     *
     * @param portRules A map where keys are rule identifiers and values are lists of port numbers to match
     * @return A map of parsed port rules, with each rule capable of matching against port numbers
     *
     * @throws NumberFormatException if the content cannot be parsed as an integer
     *
     * @see AbstractMatcher
     * @see MatchResult
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
     * Parses IP address rules from a raw map of IP address lists.
     *
     * @param raw A map where keys represent rule identifiers and values are lists of IP address strings
     * @return A map of parsed IP rules, with each rule represented by a {@code BtnRuleIpMatcher}
     * 
     * @throws IllegalArgumentException if an IP address cannot be parsed
     * 
     * @see IPAddressUtil#getIPAddress(String)
     * @see DualIPv4v6AssociativeTries
     * @see BtnRuleIpMatcher
     */
    public Map<String, List<Rule>> parseIPRule(Map<String, List<String>> raw) {
        Map<String, List<Rule>> rules = new HashMap<>();
        raw.forEach((k, v) -> {
            DualIPv4v6AssociativeTries<String> tries = new DualIPv4v6AssociativeTries<>();
            v.stream().map(IPAddressUtil::getIPAddress).forEach(tries::add);
            rules.put(k, List.of(new BtnRuleIpMatcher(version, k, k, List.of(tries))));
        });
        return rules;
    }

    public Map<String, List<Rule>> parseRule(Map<String, List<String>> raw) {
        Map<String, List<Rule>> rules = new HashMap<>();
        raw.forEach((k, v) -> rules.put(k, RuleParser.parse(v)));
        return rules;
    }

    public static class BtnRuleIpMatcher extends IPMatcher {

        private final String version;

        /**
         * Constructs a BtnRuleIpMatcher for IP-based rule matching.
         *
         * @param version     The version of the IP rule set
         * @param ruleId     Unique identifier for the rule
         * @param ruleName   Human-readable name of the rule
         * @param ruleData   List of dual IPv4/IPv6 associative tries containing IP address rules
         */
        public BtnRuleIpMatcher(String version, String ruleId, String ruleName, List<DualIPv4v6AssociativeTries<String>> ruleData) {
            super(ruleId, ruleName, ruleData);
            this.version = version;
        }

        @Override
        public @NotNull TranslationComponent matcherName() {
            return new TranslationComponent(Lang.BTN_IP_RULE, version);
        }

        @Override
        public String matcherIdentifier() {
            return "btn-exception:ip";
        }
    }
}
