package com.ghostchu.peerbanhelper.util.rule.matcher;

import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.util.rule.MatchResult;
import com.ghostchu.peerbanhelper.util.rule.RuleMatcher;
import com.ghostchu.peerbanhelper.util.rule.SubRuleType;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import inet.ipaddr.IPAddress;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Slf4j
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class IPMatcher extends RuleMatcher {

    private List<IPAddress> subnets;
    private List<IPAddress> ips;
    private BloomFilter<String> bloomFilter;

    public IPMatcher(String ruleId, String ruleName, Object... ruleData) {
        super(SubRuleType.IP, ruleId, ruleName, ruleData);
    }

    @Override
    public void setData(String ruleName, Object... ruleData) {
        this.ruleName = ruleName;
        this.ips = ruleData.length > 0 ? (List<IPAddress>) ruleData[0] : List.of();
        this.subnets = ruleData.length > 1 ? (List<IPAddress>) ruleData[1] : List.of();
        bloomFilter = BloomFilter.create(Funnels.stringFunnel(StandardCharsets.UTF_8), ips.size(), 0.01);
        ips.forEach(ip -> bloomFilter.put(ip.toString()));
    }

    @Override
    public @NotNull MatchResult match0(@NotNull String content) {
        IPAddress pa = IPAddressUtil.getIPAddress(content);
        if (pa.isIPv4Convertible()) {
            pa = pa.toIPv4();
        }
        final IPAddress ip = pa;
        // 先用bloom过滤器查一下
        if (bloomFilter.mightContain(content)) {
            // 如果查到了，那么进一步验证到底是不是在黑名单中(bloom filter存在误报的可能性)
            if (ips.stream().anyMatch(ele -> ele.isIPv4Convertible() == ip.isIPv4Convertible() && ele.equals(ip))) {
                return MatchResult.TRUE;
            }
        }
        // 最后subnet表查一下
        if (subnets.stream().anyMatch(subnet -> subnet.contains(ip))) {
            return MatchResult.TRUE;
        }
        return MatchResult.DEFAULT;
    }

    @Override
    public Map<String, Object> metadata() {
        return Map.of("rule", ruleName);
    }

    @Override
    public @NotNull String matcherName() {
        return Lang.RULE_MATCHER_SUB_RULE;
    }

    @Override
    public String matcherIdentifier() {
        return "peerbanhelper:ipbanmatcher";
    }
}
