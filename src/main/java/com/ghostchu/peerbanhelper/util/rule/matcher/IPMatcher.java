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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class IPMatcher extends RuleMatcher<DualIPv4v6AssociativeTries<String>> {
    private volatile DualIPv4v6AssociativeTries<String> ips;

    public IPMatcher(String ruleId, String ruleName, List<DualIPv4v6AssociativeTries<String>> ruleData) {
        super(ruleId, ruleName, ruleData);
        this.ips = ruleData.getFirst();
    }

    /**
     * 设置数据
     *
     * @param ruleName 规则名
     * @param ruleData 规则数据
     */
    public void setData(String ruleName, List<DualIPv4v6AssociativeTries<String>> ruleData) {
        setRuleName(ruleName);
        this.ips = ruleData.getFirst();
    }

    public long size() {
        var snapshot = ips;
        return snapshot == null ? 0 : snapshot.size();
    }

    @NotNull
    public Map<IPAddress, String> copyAddresses() {
        Map<IPAddress, String> addresses = new HashMap<>();
        var snapshot = ips;
        if (snapshot == null) {
            return addresses;
        }
        snapshot.forEach(ip -> addresses.put(ip, snapshot.get(ip)));
        return addresses;
    }

    @Override
    public @NotNull MatchResult match0(@NotNull String content) {
        final IPAddress ip = IPAddressUtil.getIPAddress(content);
        if (ip == null) return new MatchResult(MatchResultEnum.DEFAULT, new TranslationComponent("IP is null"));
        if (ips == null) {
            return new MatchResult(MatchResultEnum.DEFAULT, new TranslationComponent("IPs set is null"));
        }
        var node = ips.elementsContaining(ip);
        if (node != null) {
            return new MatchResult(MatchResultEnum.TRUE, new TranslationComponent(node.getValue()));
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
