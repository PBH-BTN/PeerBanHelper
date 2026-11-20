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

import java.util.Arrays;
import java.util.List;
import inet.ipaddr.ipv4.IPv4AddressAssociativeTrie;

@Slf4j
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class IPMatcher extends RuleMatcher<DualIPv4v6AssociativeTries<String>> {
    private volatile RuleIndex index;

    public IPMatcher(String ruleId, String ruleName, List<DualIPv4v6AssociativeTries<String>> ruleData) {
        super(ruleId, ruleName, ruleData);
    }

    public void setData(String ruleName, List<DualIPv4v6AssociativeTries<String>> ruleData) {
        setRuleName(ruleName);
        index = buildIndex(ruleData.getFirst());
    }

    protected RuleIndex buildIndex(DualIPv4v6AssociativeTries<String> rules) {
        return new RuleIndex(rules, new int[0], new String[0]);
    }

    protected record RuleIndex(DualIPv4v6AssociativeTries<String> prefixes, int[] hosts, String[] comments) {}

    public static final class Subscription extends IPMatcher {
        public Subscription(String id, String name, List<DualIPv4v6AssociativeTries<String>> data) {
            super(id, name, data);
        }

        @Override
        protected RuleIndex buildIndex(DualIPv4v6AssociativeTries<String> rules) {
            var ipv4 = rules.getIPv4Trie();
            var prefixes = new DualIPv4v6AssociativeTries<>(new IPv4AddressAssociativeTrie<String>(), rules.getIPv6Trie());
            int[] hosts = new int[ipv4.size()];
            String[] comments = new String[hosts.length];
            int count = 0;
            var nodes = ipv4.nodeIterator(true);
            while (nodes.hasNext()) {
                var node = nodes.next();
                var address = node.getKey();
                if (address.isMultiple()) {
                    prefixes.put(address, node.getValue());
                } else {
                    // Trie iteration orders addresses as unsigned IPv4. Flip the sign bit for binarySearch.
                    hosts[count] = address.intValue() ^ Integer.MIN_VALUE;
                    comments[count++] = node.getValue();
                }
            }
            if (count == 0) return super.buildIndex(rules);
            return new RuleIndex(prefixes, Arrays.copyOf(hosts, count), Arrays.copyOf(comments, count));
        }
    }

    public long size() {
        var current = index;
        return (long) current.prefixes.size() + current.hosts.length;
    }

    @Override
    public @NotNull MatchResult match0(@NotNull String content) {
        final IPAddress ip = IPAddressUtil.getIPAddress(content);
        if (ip == null) return new MatchResult(MatchResultEnum.DEFAULT, new TranslationComponent("IP is null"));
        var current = index;
        var node = current.prefixes.elementsContaining(ip);
        if (node != null) {
            return new MatchResult(MatchResultEnum.TRUE, new TranslationComponent(node.getValue()));
        }
        if (current.hosts.length != 0 && ip.isIPv4() && !ip.isMultiple()) {
            int pos = Arrays.binarySearch(current.hosts, ip.toIPv4().intValue() ^ Integer.MIN_VALUE);
            if (pos >= 0) return new MatchResult(MatchResultEnum.TRUE, new TranslationComponent(current.comments[pos]));
        }
        return new MatchResult(MatchResultEnum.DEFAULT, new TranslationComponent("Given IP not in IPs set"));
    }

    @Override
    public TranslationComponent matcherName() {
        return new TranslationComponent(Lang.RULE_MATCHER_SUB_RULE, getRuleName());
    }

    @Override
    public String matcherIdentifier() {
        return "peerbanhelper:ipmatcher";
    }
}
