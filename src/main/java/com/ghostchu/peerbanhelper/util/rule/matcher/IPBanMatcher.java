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
    private List<IPAddress> ips;
    private BloomFilter bloomFilter;

    public IPBanMatcher(String ruleId, String ruleName, List<IPAddress> ips) {
        this.ruleId = ruleId;
        setData(ruleName, ips);
    }

    public void setData(String ruleName, List<IPAddress> ips){
        this.ruleName = ruleName;
        this.ips = ips;
        bloomFilter = new BitSetBloomFilter(ips.size() * 2, ips.size(), 8);
        ips.forEach(ip -> bloomFilter.add(ip.toString()));
    }

    @Override
    public @NotNull MatchResult match0(@NotNull String content) {
        // 先用bloom过滤器查一下，如果没查到那么必然不在黑名单中
        if (!bloomFilter.contains(content)) {
            return MatchResult.DEFAULT;
        }
        // 如果查到了，那么进一步验证到底是不是在黑名单中(bloom filter存在误报的可能性)
        IPAddress pa = IPAddressUtil.getIPAddress(content);
        int counter = 0;
        for (IPAddress ra : ips) {
            if (ra.isIPv4() != pa.isIPv4()) {
                counter++;
                continue;
            }
            if (ra.equals(pa) || ra.contains(pa)) {
                return MatchResult.TRUE;
            }
            counter++;
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
        return "peerbanhelper:subrulematcher";
    }
}
