package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.btn.BtnNetwork;
import com.ghostchu.peerbanhelper.btn.BtnRuleParsed;
import com.ghostchu.peerbanhelper.btn.ability.BtnAbilityRules;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.BanResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.NullUtil;
import com.ghostchu.peerbanhelper.util.rule.Rule;
import com.ghostchu.peerbanhelper.util.rule.RuleMatchResult;
import com.ghostchu.peerbanhelper.util.rule.RuleParser;
import inet.ipaddr.IPAddress;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.ExecutorService;

public class BtnNetworkOnline extends AbstractFeatureModule {

    public BtnNetworkOnline(PeerBanHelperServer server, YamlConfiguration profile) {
        super(server, profile);
    }

    @Override
    public @NotNull String getName() {
        return "BTN Network Online Rules";
    }

    @Override
    public @NotNull String getConfigName() {
        return "btn";
    }

    @Override
    public boolean needCheckHandshake() {
        return false;
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
    public @NotNull BanResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull ExecutorService ruleExecuteExecutor) {
        BtnNetwork manager = getServer().getBtnNetwork();
        if (manager == null) {
            return new BanResult(this, PeerAction.NO_ACTION, "N/A", "BtnManager not initialized");
        }
        BtnAbilityRules ruleAbility = (BtnAbilityRules) manager.getAbilities().get(BtnAbilityRules.class);
        if (ruleAbility == null) {
            return new BanResult(this, PeerAction.NO_ACTION, "N/A", "BtnRulesAbility not get ready yet");
        }
        BtnRuleParsed rule = ruleAbility.getBtnRule();
        if (rule == null) {
            return new BanResult(this, PeerAction.NO_ACTION, "N/A", "BtnRules not get ready yet");
        }
        BanResult result = null;
        if (rule.getPeerIdRules() != null) {
            result = NullUtil.anyNotNull(result, checkPeerIdRule(rule, torrent, peer, ruleExecuteExecutor));
        }
        if (rule.getClientNameRules() != null) {
            result = NullUtil.anyNotNull(result, checkClientNameRule(rule, torrent, peer, ruleExecuteExecutor));
        }
        if (rule.getIpRules() != null) {
            result = NullUtil.anyNotNull(result, checkIpRule(rule, torrent, peer, ruleExecuteExecutor));
        }
        if (rule.getPortRules() != null) {
            result = NullUtil.anyNotNull(result, checkPortRule(rule, torrent, peer, ruleExecuteExecutor));
        }
        if (result == null) {
            return new BanResult(this, PeerAction.NO_ACTION, "N/A", "OK!");
        }
        return result;
    }

    private BanResult checkPortRule(BtnRuleParsed rule, Torrent torrent, Peer peer, ExecutorService ruleExecuteExecutor) {
        for (String category : rule.getPortRules().keySet()) {
            RuleMatchResult matchResult = RuleParser.matchRule(rule.getPortRules().get(category), peer.getPeerId());
            if (matchResult.hit()) {
                return new BanResult(this, PeerAction.BAN, "BTN-" + category + "-" + matchResult.rule(), String.format(Lang.MODULE_BTN_BAN, "Port", category, matchResult.rule()));
            }
        }
        return null;
    }

    @Nullable
    private BanResult checkClientNameRule(BtnRuleParsed rule, Torrent torrent, Peer peer, ExecutorService ruleExecuteExecutor) {
        for (String category : rule.getClientNameRules().keySet()) {
            List<Rule> rules = rule.getClientNameRules().get(category);
            RuleMatchResult matchResult = RuleParser.matchRule(rules, peer.getClientName());
            if (matchResult.hit()) {
                return new BanResult(this, PeerAction.BAN, "BTN-" + category + "-" + matchResult.rule(), String.format(Lang.MODULE_BTN_BAN, "ClientName", category, matchResult.rule()));
            }
        }
        return null;
    }

    @Nullable
    private BanResult checkPeerIdRule(BtnRuleParsed rule, Torrent torrent, Peer peer, ExecutorService ruleExecuteExecutor) {
        for (String category : rule.getPeerIdRules().keySet()) {
            List<Rule> rules = rule.getPeerIdRules().get(category);
            RuleMatchResult matchResult = RuleParser.matchRule(rules, peer.getClientName());
            if (matchResult.hit()) {
                return new BanResult(this, PeerAction.BAN, "BTN-" + category + "-" + matchResult.rule(), String.format(Lang.MODULE_BTN_BAN, "PeerId", category, matchResult.rule()));
            }
        }
        return null;
    }

    @Nullable
    private BanResult checkIpRule(BtnRuleParsed rule, @NotNull Torrent torrent, @NotNull Peer peer, @NotNull ExecutorService ruleExecuteExecutor) {
        IPAddress pa = peer.getAddress().getAddress();
        if (pa == null) return null;
        if (pa.isIPv4Convertible()) {
            pa = pa.toIPv4();
        }
        for (String category : rule.getIpRules().keySet()) {
            RuleMatchResult matchResult = RuleParser.matchRule(rule.getIpRules().get(category), pa.toString());
            if (matchResult.hit()) {
                return new BanResult(this, PeerAction.BAN, "BTN-" + category + "-" + matchResult.rule(), String.format(Lang.MODULE_BTN_BAN, "IP", category, pa));
            }
        }
        return null;
    }
}
