package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.btn.BtnNetwork;
import com.ghostchu.peerbanhelper.btn.BtnRuleParsed;
import com.ghostchu.peerbanhelper.btn.ability.BtnAbilityRules;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.module.AbstractRuleFeatureModule;
import com.ghostchu.peerbanhelper.module.CheckResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.NullUtil;
import com.ghostchu.peerbanhelper.util.context.IgnoreScan;
import com.ghostchu.peerbanhelper.util.rule.Rule;
import com.ghostchu.peerbanhelper.util.rule.RuleMatchResult;
import com.ghostchu.peerbanhelper.util.rule.RuleParser;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
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
@IgnoreScan
public class BtnNetworkOnline extends AbstractRuleFeatureModule implements Reloadable {
    private final CheckResult BTN_MANAGER_NOT_INITIALIZED = new CheckResult(getClass(), PeerAction.NO_ACTION, 0, new TranslationComponent(Lang.GENERAL_NA), new TranslationComponent("BtnManager not initialized"));
    @Autowired(required = false)
    private BtnNetwork manager;
    private long banDuration;

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
        Main.getReloadManager().register(this);
    }

    @Override
    public void onDisable() {
        Main.getReloadManager().unregister(this);
    }

    @Override
    public ReloadResult reloadModule() throws Exception {
        reloadConfig();
        return Reloadable.super.reloadModule();
    }

    public void reloadConfig() {
        this.banDuration = getConfig().getLong("ban-duration", 0);
    }

    @Override
    public boolean isThreadSafe() {
        return true;
    }

    @Override
    public @NotNull CheckResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer , @NotNull Downloader downloader, @NotNull ExecutorService ruleExecuteExecutor) {
        if (manager == null) {
            return BTN_MANAGER_NOT_INITIALIZED;
        }
        BtnAbilityRules ruleAbility = (BtnAbilityRules) manager.getAbilities().get(BtnAbilityRules.class);
        if (ruleAbility == null) {
            return pass();
        }
        BtnRuleParsed rule = ruleAbility.getBtnRule();
        if (rule == null) {
            return pass();
        }
        if (isHandShaking(peer)) {
            return handshaking();
        }
        return getCache().readCacheButWritePassOnly(this, "peer-" + peer.getCacheKey(), () -> {
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
                return new CheckResult(getClass(), PeerAction.BAN, banDuration, new TranslationComponent(Lang.BTN_BTN_RULE, category, matchResult.rule().matcherIdentifier()), new TranslationComponent(Lang.MODULE_BTN_BAN, "Port", category, matchResult.rule().toString()));
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
                return new CheckResult(getClass(), PeerAction.BAN, banDuration, new TranslationComponent(Lang.BTN_BTN_RULE, category, matchResult.rule().matcherIdentifier()), new TranslationComponent(Lang.MODULE_BTN_BAN, "ClientName", category, matchResult.rule().toString()));
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
                return new CheckResult(getClass(), PeerAction.BAN, banDuration, new TranslationComponent(Lang.BTN_BTN_RULE, category, matchResult.rule().matcherIdentifier()), new TranslationComponent(Lang.MODULE_BTN_BAN, "PeerId", category, matchResult.rule().toString()));
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
                return new CheckResult(getClass(), PeerAction.BAN, banDuration, new TranslationComponent(Lang.BTN_BTN_RULE, category, matchResult.rule().matcherIdentifier()), new TranslationComponent(Lang.MODULE_BTN_BAN, "IP", category, pa.toString()));
            }
        }
        return null;
    }
}
