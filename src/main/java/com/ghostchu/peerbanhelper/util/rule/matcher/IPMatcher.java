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
     * Sets the rule name and updates the IP address data for this matcher.
     *
     * @param ruleName The name of the rule to be set
     * @param ruleData A list of DualIPv4v6AssociativeTries containing IP address data, where the first element will be used
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
     * @return A {@code MatchResult} indicating the outcome of the IP matching process
     *         - Returns {@code MatchResultEnum.DEFAULT} with a message if the IP is null or the IP set is empty
     *         - Returns {@code MatchResultEnum.TRUE} with the associated value if the IP is found in the set
     *         - Returns {@code MatchResultEnum.DEFAULT} with a message if the IP is not in the set
     *
     * @throws NullPointerException if the input content is null (prevented by {@code @NotNull} annotation)
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
     * Provides a translation component representing the name of this IP matcher.
     *
     * @return A {@link TranslationComponent} containing the matcher's rule name
     *         for internationalization and localization purposes
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
