package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.btn.BtnNetwork;
import com.ghostchu.peerbanhelper.btn.BtnRuleParsed;
import com.ghostchu.peerbanhelper.btn.ability.BtnAbilityRules;
import com.ghostchu.peerbanhelper.module.AbstractRuleFeatureModule;
import com.ghostchu.peerbanhelper.module.CheckResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.NullUtil;
import com.ghostchu.peerbanhelper.util.rule.Rule;
import com.ghostchu.peerbanhelper.util.rule.RuleMatchResult;
import com.ghostchu.peerbanhelper.util.rule.RuleParser;
import inet.ipaddr.IPAddress;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;

@Slf4j
@Component
public class BtnNetworkOnline extends AbstractRuleFeatureModule {
    private final CheckResult BTN_MANAGER_NOT_INITIALIZED = new CheckResult(getClass(), PeerAction.NO_ACTION, "N/A", "BtnManager not initialized");
    @Autowired(required = false)
    private BtnNetwork manager;

    @Override
    public @NotNull String getName() {
        return "BTN Network Online Rules";
    }

    @Override
    public @NotNull String getConfigName() {
        return "btn";
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public void onEnable() {
        reloadConfig();
    }

    @Override
    public void onDisable() {

    }

    public void reloadConfig() {

    }

    @Override
    public @NotNull CheckResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull ExecutorService ruleExecuteExecutor) {
        if (manager == null) {
            return BTN_MANAGER_NOT_INITIALIZED;
        }
        BtnAbilityRules ruleAbility = (BtnAbilityRules) manager.getAbilities().get(BtnAbilityRules.class);
        if (ruleAbility == null) {
            return new CheckResult(getClass(), PeerAction.NO_ACTION, "N/A", "BtnRulesAbility not get ready yet");
        }
        BtnRuleParsed rule = ruleAbility.getBtnRule();
        if (rule == null) {
            return new CheckResult(getClass(), PeerAction.NO_ACTION, "N/A", "BtnRules not get ready yet");
        }
        if (isHandShaking(peer)) {
            return handshaking();
        }
        return (CheckResult) getCache().readCache(this, "peer-" + peer.getCacheKey(), () -> {
            CheckResult r = null;
            if (rule.getPeerIdRules() != null) {
                r = NullUtil.anyNotNull(r, checkPeerIdRule(rule, torrent, peer, ruleExecuteExecutor));
            }
            if (rule.getClientNameRules() != null) {
                r = NullUtil.anyNotNull(r, checkClientNameRule(rule, torrent, peer, ruleExecuteExecutor));
            }
            if (rule.getIpRules() != null) {
                r = NullUtil.anyNotNull(r, checkIpRule(rule, torrent, peer, ruleExecuteExecutor));
            }
            if (rule.getPortRules() != null) {
                r = NullUtil.anyNotNull(r, checkPortRule(rule, torrent, peer, ruleExecuteExecutor));
            }
            if (r == null) {
                return pass();
            }
            return r;
        }, true);
    }

    private CheckResult checkPortRule(BtnRuleParsed rule, Torrent torrent, Peer peer, ExecutorService ruleExecuteExecutor) {
        for (String category : rule.getPortRules().keySet()) {
            RuleMatchResult matchResult = RuleParser.matchRule(rule.getPortRules().get(category), peer.getPeerId());
            if (matchResult.hit()) {
                return new CheckResult(getClass(), PeerAction.BAN, "BTN-" + category + "-" + matchResult.rule(), String.format(Lang.MODULE_BTN_BAN, "Port", category, matchResult.rule()));
            }
        }
        return null;
    }

    @Nullable
    private CheckResult checkClientNameRule(BtnRuleParsed rule, Torrent torrent, Peer peer, ExecutorService ruleExecuteExecutor) {
        for (String category : rule.getClientNameRules().keySet()) {
            List<Rule> rules = rule.getClientNameRules().get(category);
            RuleMatchResult matchResult = RuleParser.matchRule(rules, peer.getClientName());
            if (matchResult.hit()) {
                return new CheckResult(getClass(), PeerAction.BAN, "BTN-" + category + "-" + matchResult.rule(), String.format(Lang.MODULE_BTN_BAN, "ClientName", category, matchResult.rule()));
            }
        }
        return null;
    }

    @Nullable
    private CheckResult checkPeerIdRule(BtnRuleParsed rule, Torrent torrent, Peer peer, ExecutorService ruleExecuteExecutor) {
        for (String category : rule.getPeerIdRules().keySet()) {
            List<Rule> rules = rule.getPeerIdRules().get(category);
            RuleMatchResult matchResult = RuleParser.matchRule(rules, peer.getClientName());
            if (matchResult.hit()) {
                return new CheckResult(getClass(), PeerAction.BAN, "BTN-" + category + "-" + matchResult.rule(), String.format(Lang.MODULE_BTN_BAN, "PeerId", category, matchResult.rule()));
            }
        }
        return null;
    }

    @Nullable
    private CheckResult checkIpRule(BtnRuleParsed rule, @NotNull Torrent torrent, @NotNull Peer peer, @NotNull ExecutorService ruleExecuteExecutor) {
        IPAddress pa = peer.getPeerAddress().getAddress();
        if (pa == null) return null;
        if (pa.isIPv4Convertible()) {
            pa = pa.toIPv4();
        }
        for (String category : rule.getIpRules().keySet()) {
            RuleMatchResult matchResult = RuleParser.matchRule(rule.getIpRules().get(category), pa.toString());
            if (matchResult.hit()) {
                return new CheckResult(getClass(), PeerAction.BAN, "BTN-" + category + "-" + matchResult.rule(), String.format(Lang.MODULE_BTN_BAN, "IP", category, pa));
            }
        }
        return null;
    }
}
