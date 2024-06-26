package com.ghostchu.peerbanhelper.util.rule.matcher;

import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.util.rule.MatchResult;
import com.ghostchu.peerbanhelper.util.rule.RuleMatcher;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import inet.ipaddr.IPAddress;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class IPMatcher extends RuleMatcher<IPAddress> {

    private List<IPAddress> subnets;
    private List<IPAddress> ips;
    private BloomFilter<String> bloomFilter;

    public IPMatcher(String ruleId, String ruleName, List<IPAddress> ruleData) {
        super(ruleId, ruleName, ruleData);
    }

    /**
     * 设置数据
     * 其中ipv4网段地址转为精确ip
     * 考虑到ipv6分配地址通常是/64，所以ipv6网段不转为精确ip
     *
     * @param ruleName 规则名
     * @param ruleData 规则数据
     */
    public void setData(@NotNull String ruleName, List<IPAddress> ruleData) {
        setRuleName(ruleName);
        this.ips = new ArrayList<>();
        this.subnets = new ArrayList<>();
        ruleData.forEach(ipAddress -> {
            // 判断是否是网段
            List<IPAddress> ipsList = new ArrayList<>();
            if (null != ipAddress.getNetworkPrefixLength()) {
                if (ipAddress.isIPv4() && ipAddress.getNetworkPrefixLength() >= 20) {
                    // 前缀长度 >= 20 的ipv4网段地址转为精确ip
                    ipAddress.nonZeroHostIterator().forEachRemaining(ipsList::add);
                } else {
                    this.subnets.add(ipAddress);
                    log.debug(Lang.IP_BAN_RULE_LOAD_CIDR, ruleName, ipAddress);
                }
            } else {
                ipsList.add(ipAddress);
            }
            ipsList.forEach(ip -> {
                ip = ip.withoutPrefixLength();
                this.ips.add(ip);
                log.debug(Lang.IP_BAN_RULE_LOAD_IP, ruleName, ip);
            });
        });
        bloomFilter = BloomFilter.create(Funnels.stringFunnel(StandardCharsets.UTF_8), this.ips.size(), 0.01);
        this.ips.forEach(ip -> bloomFilter.put(ip.toString()));
    }

    @Override
    public @NotNull MatchResult match0(@NotNull String content) {
        final IPAddress ip = IPAddressUtil.getIPAddress(content);
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
    public @NotNull String matcherName() {
        return Lang.RULE_MATCHER_SUB_RULE;
    }

    @Override
    public String matcherIdentifier() {
        return "peerbanhelper:ipmatcher";
    }
}
