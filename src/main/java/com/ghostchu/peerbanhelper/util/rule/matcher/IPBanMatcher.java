package com.ghostchu.peerbanhelper.util.rule.matcher;

import cn.hutool.bloomfilter.BitSetBloomFilter;
import cn.hutool.bloomfilter.BloomFilter;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.util.rule.AbstractMatcher;
import com.ghostchu.peerbanhelper.util.rule.MatchResult;
import inet.ipaddr.IPAddress;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

@Slf4j
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class IPBanMatcher extends AbstractMatcher {

    @Getter
    private final String ruleId;

    @Getter
    private String ruleName;
    private List<IPAddress> subnets;
    private List<IPAddress> ips;
    private BloomFilter bloomFilter;

    public IPBanMatcher(String ruleId, String ruleName, List<IPAddress> ips, List<IPAddress> subnets) {
        this.ruleId = ruleId;
        setData(ruleName, ips, subnets);
    }

    public void setData(String ruleName, List<IPAddress> ips, List<IPAddress> subnets) {
        this.ruleName = ruleName;
        this.ips = ips;
        this.subnets = subnets;
        bloomFilter = new BitSetBloomFilter(ips.size() * 2, ips.size(), 8);
        ips.forEach(ip -> bloomFilter.add(ip.toString()));
    }

    @Override
    public @NotNull MatchResult match0(@NotNull String content) {
        IPAddress pa = IPAddressUtil.getIPAddress(content);
        if (pa.isIPv4Convertible()) {
            pa = pa.toIPv4();
        }
        final IPAddress ip = pa;
        // 先用bloom过滤器查一下
        if (bloomFilter.contains(content)) {
            // 如果查到了，那么进一步验证到底是不是在黑名单中(bloom filter存在误报的可能性)
            if (ips.stream().anyMatch(ele -> ele.isIPv4Convertible() == ip.isIPv4Convertible() && (ele.equals(ip) || ele.contains(ip)))) {
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
