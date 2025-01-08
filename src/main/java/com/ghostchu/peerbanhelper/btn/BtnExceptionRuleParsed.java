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
     * Parses port rules from a map of port configurations and generates matching rules.
     *
     * @param portRules A map where keys represent rule identifiers and values are lists of port numbers to match
     * @return A map of rule identifiers to lists of port matching rules, where each rule is an AbstractMatcher
     *
     * @throws NumberFormatException if the content cannot be parsed as an integer during matching
     *
     * This method creates custom AbstractMatcher instances for each port number, which can:
     * - Match a given content against a specific port number
     * - Provide metadata about the matched port
     * - Generate translation components for match results
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
     * Parses IP rules from a raw map of IP addresses and converts them into a map of matching rules.
     *
     * @param raw A map where keys represent rule identifiers and values are lists of IP address strings
     * @return A map of rule identifiers to lists of IP matching rules, using DualIPv4v6AssociativeTries
     *
     * @throws IllegalArgumentException if an invalid IP address is encountered during parsing
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
         * Constructs a BtnRuleIpMatcher with version and IP matching configuration.
         *
         * @param version    The version identifier for the IP rule
         * @param ruleId     Unique identifier for the IP matching rule
         * @param ruleName   Human-readable name of the IP rule
         * @param ruleData   List of dual IPv4/IPv6 associative tries containing IP address matching data
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
