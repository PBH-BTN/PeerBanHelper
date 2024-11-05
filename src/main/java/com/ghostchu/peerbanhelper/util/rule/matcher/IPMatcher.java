package com.ghostchu.peerbanhelper.util.rule.matcher;

import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.util.rule.MatchResult;
import com.ghostchu.peerbanhelper.util.rule.RuleMatcher;
import inet.ipaddr.IPAddress;
import inet.ipaddr.ipv4.IPv4AddressTrie;
import inet.ipaddr.ipv6.IPv6AddressTrie;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Slf4j
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class IPMatcher extends RuleMatcher<IPAddress> {
    private IPv4AddressTrie ipv4;
    private IPv6AddressTrie ipv6;

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
    public void setData(String ruleName, List<IPAddress> ruleData) {
        setRuleName(ruleName);
        this.ipv4 = new IPv4AddressTrie();
        this.ipv6 = new IPv6AddressTrie();
        ruleData.forEach(ipAddress -> {
            if (ipAddress.isIPv4()) {
                ipv4.add(ipAddress.toIPv4());
            } else if (ipAddress.isIPv6()) {
                ipv6.add(ipAddress.toIPv6());
            } else {
                log.debug("The address {} neither IPv4 or IPv6 addresses", ipAddress);
            }
        });
    }

    @Override
    public @NotNull MatchResult match0(@NotNull String content) {
        final IPAddress ip = IPAddressUtil.getIPAddress(content);
        if (ip == null) return MatchResult.DEFAULT;
        if (ip.isIPv4()) {
            if (ipv4.contains(ip.toIPv4())) {
                return MatchResult.TRUE;
            }
        } else if (ip.isIPv6()) {
            if (ipv6.contains(ip.toIPv6())) {
                return MatchResult.TRUE;
            }
        } else {
            log.debug("The address {} neither IPv4 or IPv6 addresses", ip);
        }
        return MatchResult.DEFAULT;
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
