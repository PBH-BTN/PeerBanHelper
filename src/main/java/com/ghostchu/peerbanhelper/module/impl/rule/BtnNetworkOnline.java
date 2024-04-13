package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.btn.BtnManager;
import com.ghostchu.peerbanhelper.btn.BtnNetwork;
import com.ghostchu.peerbanhelper.btn.BtnRule;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.BanResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.NullUtil;
import com.ghostchu.peerbanhelper.util.RuleParseHelper;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.ExecutorService;

public class BtnNetworkOnline extends AbstractFeatureModule {
    private List<String> bannedPeers;

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
        BtnManager manager = getServer().getBtnManager();
        BtnNetwork network = manager.getNetwork();
        BtnRule rule = network.getRule();
        if (rule == null) {
            return new BanResult(this, PeerAction.NO_ACTION, "BtnRules not get ready yet");
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
        if (result == null) {
            return new BanResult(this, PeerAction.NO_ACTION, "OK!");
        }
        return result;
    }
    @Nullable
    private BanResult checkClientNameRule(BtnRule rule, Torrent torrent, Peer peer, ExecutorService ruleExecuteExecutor) {
        for (String category : rule.getClientNameRules().keySet()) {
            List<String> rules = rule.getClientNameRules().get(category);
            for (String ruleContent : rules) {
                if (RuleParseHelper.match(peer.getPeerId(), ruleContent)) {
                    return new BanResult(this, PeerAction.BAN, String.format(Lang.MODULE_BTN_BAN, "PeerId", category, ruleContent));
                }
            }
        }
        return null;
    }

    @Nullable
    private BanResult checkPeerIdRule(BtnRule rule, Torrent torrent, Peer peer, ExecutorService ruleExecuteExecutor) {
        for (String category : rule.getPeerIdRules().keySet()) {
            List<String> rules = rule.getPeerIdRules().get(category);
            for (String ruleContent : rules) {
                if (RuleParseHelper.match(peer.getPeerId(), ruleContent)) {
                    return new BanResult(this, PeerAction.BAN, String.format(Lang.MODULE_BTN_BAN, "PeerId", category, ruleContent));
                }
            }
        }
        return null;
    }

    @Nullable
    private BanResult checkIpRule(BtnRule rule, @NotNull Torrent torrent, @NotNull Peer peer, @NotNull ExecutorService ruleExecuteExecutor) {
        IPAddress pa =peer.getAddress().getAddress();
        if(pa == null) return null;
        if(pa.isIPv4Convertible()){
            pa = pa.toIPv4();
        }
        for (String category : rule.getIpRules().keySet()) {
            List<String> rules = rule.getIpRules().get(category);
            for (String ruleContent : rules) {
                IPAddress ra = new IPAddressString(ruleContent).getAddress();
                if(ra == null) continue;
                if(ra.equals(pa) || ra.contains(pa)){
                    return new BanResult(this, PeerAction.BAN, String.format(Lang.MODULE_BTN_BAN, "IP", category, ruleContent));
                }
            }
        }
        return null;
    }


}
