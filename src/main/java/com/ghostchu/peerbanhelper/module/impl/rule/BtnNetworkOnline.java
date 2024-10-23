package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.btn.BtnNetwork;
import com.ghostchu.peerbanhelper.btn.BtnRuleParsed;
import com.ghostchu.peerbanhelper.btn.ability.BtnAbility;
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
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.ReloadStatus;
import com.ghostchu.simplereloadlib.Reloadable;
import inet.ipaddr.IPAddress;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;

@Slf4j
@Component
@IgnoreScan
public class BtnNetworkOnline extends AbstractRuleFeatureModule implements Reloadable {
    private final CheckResult BTN_MANAGER_NOT_INITIALIZED = new CheckResult(getClass(), PeerAction.NO_ACTION, 0, new TranslationComponent(Lang.GENERAL_NA), new TranslationComponent("BtnManager not initialized"));
    @Autowired(required = false)
    private BtnNetwork manager;
    private long banDuration;
    @Autowired
    private JavalinWebContainer javalinWebContainer;
    @Autowired
    private BtnNetwork btnNetwork;

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
        javalinWebContainer.javalin()
                .get("/api/modules/btn", this::status, Role.USER_READ);
    }

    private void status(Context context) {
        if (btnNetwork == null) {
            context.json(new StdResp(false, tl(locale(context), Lang.BTN_NOT_ENABLE_AND_REQUIRE_RESTART), null));
            return;
        }
        Map<String, Object> info = new HashMap<>();
        info.put("configSuccess", btnNetwork.getConfigSuccess());
        var abilities = new ArrayList<>();
        for (Map.Entry<Class<? extends BtnAbility>, BtnAbility> entry : btnNetwork.getAbilities().entrySet()) {
            Map<String, Object> abilityStatus = new HashMap<>();
            abilityStatus.put("name", entry.getValue().getName());
            abilityStatus.put("displayName", tl(locale(context), entry.getValue().getDisplayName()));
            abilityStatus.put("description", tl(locale(context), entry.getValue().getDescription()));
            abilityStatus.put("lastSuccess", entry.getValue().lastStatus());
            abilityStatus.put("lastMessage", tl(locale(context), entry.getValue().lastMessage()));
            abilityStatus.put("lastUpdateAt", entry.getValue().lastStatusAt());
            abilities.add(abilityStatus);
        }
        info.put("abilities", abilities);
        info.put("appId", btnNetwork.getAppId());
        String appSecret;
        if (btnNetwork.getAppSecret().length() > 5) {
            appSecret = btnNetwork.getAppSecret().substring(0, 5) + "*******";
        } else {
            appSecret = "******";
        }
        info.put("appSecret", appSecret);
        info.put("configUrl", btnNetwork.getConfigUrl());
        context.json(new StdResp(true, null, info));
    }

    @Override
    public boolean isModuleEnabled() {
        if (super.isModuleEnabled()) {
            return btnNetwork != null;
        }
        return false;
    }

    @Override
    public void onDisable() {
        Main.getReloadManager().unregister(this);
    }

    @Override
    public ReloadResult reloadModule() throws Exception {
        boolean actualLoaded = btnNetwork != null;
        boolean configLoaded = Main.getMainConfig().getBoolean("btn.enabled");
        if (actualLoaded != configLoaded) {
            return ReloadResult.builder().status(ReloadStatus.REQUIRE_RESTART).build();
        } else {
            reloadConfig();
            return ReloadResult.builder().status(ReloadStatus.SUCCESS).build();
        }

    }

    public void reloadConfig() {
        this.banDuration = getConfig().getLong("ban-duration", 0);
        getCache().invalidateAll();
    }

    @Override
    public boolean isThreadSafe() {
        return true;
    }

    @Override
    public @NotNull CheckResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull Downloader downloader, @NotNull ExecutorService ruleExecuteExecutor) {
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
