package com.ghostchu.peerbanhelper.util.rule.matcher;

import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.util.rule.MatchResult;
import com.ghostchu.peerbanhelper.util.rule.MatchResultEnum;
import com.ghostchu.peerbanhelper.util.rule.RuleMatcher;
import inet.ipaddr.IPAddress;
import inet.ipaddr.format.util.DualIPv4v6AssociativeTries;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Slf4j
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class IPMatcher extends RuleMatcher<DualIPv4v6AssociativeTries<String>> {
    private DualIPv4v6AssociativeTries<String> ips;

    /**
     * Constructs an IPMatcher with the specified rule ID, rule name, and rule data.
     *
     * @param ruleId The unique identifier for this IP matching rule
     * @param ruleName The name of the IP matching rule
     * @param ruleData A list of DualIPv4v6AssociativeTries containing IP address data
     * @throws IllegalArgumentException if the ruleData list is empty
     */
    public IPMatcher(String ruleId, String ruleName, List<DualIPv4v6AssociativeTries<String>> ruleData) {
        super(ruleId, ruleName, ruleData);
        this.ips = ruleData.getFirst();
    }

    /**
     * Sets the rule name and IP address data for the matcher.
     *
     * @param ruleName The name of the rule to be set
     * @param ruleData A list of dual IPv4/IPv6 associative tries containing IP address data
     */
    public void setData(String ruleName, List<DualIPv4v6AssociativeTries<String>> ruleData) {
        setRuleName(ruleName);
        this.ips = ruleData.getFirst();
    }

    public long size() {
        return ips.size();
    }

    /**
     * Matches an input IP address against a predefined set of IP addresses.
     *
     * @param content A string representation of an IP address to match
     * @return A {@code MatchResult} indicating the match status and associated translation
     *
     * @throws NullPointerException if the input content is null
     *
     * Possible match results:
     * - Returns {@code MatchResult.TRUE} if the IP is found in the set
     * - Returns {@code MatchResult.DEFAULT} with a descriptive message in these cases:
     *   1. Input IP address cannot be parsed
     *   2. IP address set is null
     *   3. Input IP address is not in the set
     */
    @Override
    public @NotNull MatchResult match0(@NotNull String content) {
        final IPAddress ip = IPAddressUtil.getIPAddress(content);
        if (ip == null) return new MatchResult(MatchResultEnum.DEFAULT, new TranslationComponent("IP is null"));
        if (ips == null) {
            new MatchResult(MatchResultEnum.DEFAULT, new TranslationComponent("IPs set is null"));
        }
        if (ips.elementContains(ip)) {
            return new MatchResult(MatchResultEnum.TRUE, new TranslationComponent(ips.get(ip)));
        }
        return new MatchResult(MatchResultEnum.DEFAULT, new TranslationComponent("Given IP not in IPs set"));
    }

    /**
     * Returns the translation component representing the matcher's name.
     *
     * @return A {@code TranslationComponent} containing the matcher's name, 
     *         which is derived from the current rule name and uses the {@code RULE_MATCHER_SUB_RULE} translation key.
     */
    @Override
    public TranslationComponent matcherName() {
        return new TranslationComponent(Lang.RULE_MATCHER_SUB_RULE, getRuleName());
    }

    @Override
    public String matcherIdentifier() {
        return "peerbanhelper:ipmatcher";
    }
}
