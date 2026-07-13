package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.BanList;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.banpipeline.PipelineTask;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.btn.BtnNetwork;
import com.ghostchu.peerbanhelper.btn.BtnRulesetParsed;
import com.ghostchu.peerbanhelper.btn.ability.impl.BtnAbilityIPAllowList;
import com.ghostchu.peerbanhelper.btn.ability.impl.BtnAbilityIPDenyList;
import com.ghostchu.peerbanhelper.btn.ability.impl.BtnAbilityRules;
import com.ghostchu.peerbanhelper.btn.legacy.LegacyBtnExceptionRuleParsed;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.event.btn.BtnRuleUpdateEvent;
import com.ghostchu.peerbanhelper.module.AbstractRuleFeatureModule;
import com.ghostchu.peerbanhelper.module.CheckResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.SharedObject;
import com.ghostchu.peerbanhelper.util.rule.*;
import com.ghostchu.peerbanhelper.util.scriptengine.CompiledScript;
import com.ghostchu.peerbanhelper.util.scriptengine.ScriptEngineManager;
import com.ghostchu.peerbanhelper.wrapper.StructuredData;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.ReloadStatus;
import com.ghostchu.simplereloadlib.Reloadable;
import com.google.common.eventbus.Subscribe;
import com.googlecode.aviator.exception.TimeoutException;
import inet.ipaddr.IPAddress;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
@Component

public final class BtnNetworkOnline extends AbstractRuleFeatureModule implements Reloadable {
    private final CheckResult BTN_MANAGER_NOT_INITIALIZED = new CheckResult(getClass(), PeerAction.NO_ACTION, 0, new TranslationComponent(Lang.GENERAL_NA), new TranslationComponent("BtnManager not initialized"), StructuredData.create().add("status", "btn_manager_not_initialized"));
    private long banDuration;
    @Autowired(required = false)
    private BtnNetwork btnNetwork;
    @Autowired
    private ScriptEngineManager scriptEngineManager;
    private boolean allowScript;

    private final ExecutorService parallelService = Executors.newWorkStealingPool();
    @Autowired
    private BanList banList;

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
        Main.getEventBus().register(this);
    }

    @Override
    public void onDisable() {
        Main.getReloadManager().unregister(this);
    }

    @Override
    public ReloadResult reloadModule() {
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
        this.allowScript = getConfig().getBoolean("allow-script-execute");
        getCache().invalidateAll();
    }

    @Override
    public boolean isThreadSafe() {
        return true;
    }

    @Override
    public @NotNull CheckResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull Downloader downloader, @NotNull PipelineTask<?> task) {
        if (btnNetwork == null) {
            return BTN_MANAGER_NOT_INITIALIZED;
        }
        // TODO: 需要重构
        task.setComment(false, "BTN: check should skip.");
        var checkExceptionResult = checkShouldSkip(torrent, peer, downloader, task);
        if (checkExceptionResult.action() == PeerAction.SKIP) {
            return checkExceptionResult;
        }
        if (allowScript) {
            task.setComment(false, "BTN: run script.");
            var scriptResult = checkScript(torrent, peer, downloader, parallelService, task);
            if (scriptResult.action() != PeerAction.NO_ACTION) {
                return scriptResult;
            }
        }
        task.setComment(false, "BTN: check should ban (modern)");
        var result = checkShouldBanModern(torrent, peer, downloader, task);
        if (result.action() != PeerAction.NO_ACTION) {
            return result;
        }
        task.setComment(false, "BTN: check should ban (legacy)");
        return checkShouldBanLegacy(torrent, peer, downloader, task);
    }

    private @NotNull CheckResult checkShouldBanModern(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull Downloader downloader, @NotNull PipelineTask<?> task) {
        var abilityObject = btnNetwork.getAbilities().get(BtnAbilityIPDenyList.class);
        if (abilityObject == null) {
            return pass();
        }
        BtnAbilityIPDenyList denyList = (BtnAbilityIPDenyList) abilityObject;
        task.setComment(false, "CheckShouldBanModern: matching IP address with rules...");
        var result = denyList.getIpMatcher().match(peer.getPeerAddress().getAddress().toCompressedString());
        if (result.result() == MatchResultEnum.TRUE) {
            return new CheckResult(getClass(), PeerAction.BAN, banDuration,
                    new TranslationComponent(Lang.BTN_ABILITY_IP_DENYLIST_RULE, result.comment()),
                    new TranslationComponent(Lang.BTN_ABILITY_IP_DENYLIST_HIT, result.comment()),
                    StructuredData.create()
                            .add("type", "ip"));
        }
        return pass();
    }

    private @NotNull CheckResult checkShouldSkip(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull Downloader downloader, PipelineTask<?> task) {
        var abilityObject = btnNetwork.getAbilities().get(BtnAbilityIPAllowList.class);
        if (abilityObject == null) {
            return pass();
        }
        BtnAbilityIPAllowList allowList = (BtnAbilityIPAllowList) abilityObject;
        task.setComment(false, "CheckShouldSkip: matching IP address with rules...");
        var result = allowList.getIpMatcher().match(peer.getPeerAddress().getAddress().toCompressedString());
        if (result.result() == MatchResultEnum.TRUE) {
            return new CheckResult(getClass(), PeerAction.SKIP, 0,
                    new TranslationComponent(Lang.BTN_ABILITY_IP_ALLOWLIST_RULE, result.comment()),
                    new TranslationComponent(Lang.BTN_ABILITY_IP_ALLOWLIST_HIT, result.comment()),
                    StructuredData.create()
                            .add("type", "ipAllowList")
                            .add("matchedValue", result.comment()));
        }
        return pass();
    }

    private @NotNull CheckResult checkScript(Torrent torrent, Peer peer, Downloader downloader, ExecutorService ruleExecuteExecutor, PipelineTask<?> task) {
        var abilityObject = btnNetwork.getAbilities().get(BtnAbilityRules.class);
        if (abilityObject == null) {
            return pass();
        }
        BtnAbilityRules exception = (BtnAbilityRules) abilityObject;
        BtnRulesetParsed rule = exception.getBtnRule();
        if (rule == null) {
            return pass();
        }
        if (isHandShaking(peer)) {
            return handshaking();
        }

        Map<CompiledScript, CompletableFuture<CheckResult>> futures = new HashMap<>();

        for (var kvPair : rule.getScriptRules().entrySet()) {
            task.setComment(false, "checkScript: Executing script: " + kvPair.toString());
            futures.put(kvPair.getValue(), CompletableFuture.supplyAsync(() -> runExpression(kvPair.getValue(), torrent, peer, downloader, task), ruleExecuteExecutor));
        }

        CheckResult finalResult = pass();
        for (var set : futures.entrySet()) {
            task.setComment(false, "checkScript: Waiting script to complete: " + set.getKey().name());
            CheckResult result = set.getValue().join();
            if (result.action() == PeerAction.SKIP) {
                return result; // Early exit on SKIP action
            } else if (result.action() == PeerAction.BAN) {
                finalResult = result;
            }
        }

        return finalResult;
    }

    public @NotNull CheckResult runExpression(CompiledScript script, @NotNull Torrent torrent, @NotNull Peer peer, @NotNull Downloader downloader, PipelineTask<?> task) {
        return getCache().readCacheButWritePassOnly(this, script.scriptHashCode() + peer.getCacheKey(), () -> {
            CheckResult result;
            try {
                Map<String, Object> env = script.newEnv();
                env.put("torrent", torrent);
                env.put("peer", peer);
                env.put("downloader", downloader);
                env.put("cacheable", new AtomicBoolean(false));
                env.put("server", getServer());
                env.put("moduleInstance", this);
                env.put("btnNetwork", btnNetwork);
                env.put("banDuration", banDuration);
                env.put("kvStorage", SharedObject.SCRIPT_THREAD_SAFE_MAP);
                Object returns = script.execute(env);
                result = scriptEngineManager.handleResult(script, banDuration, returns);
            } catch (TimeoutException timeoutException) {
                return pass();
            } catch (Exception ex) {
                log.error(tlUI(Lang.RULE_ENGINE_ERROR, script.name()), ex);
                return pass();
            }
            if (result != null && result.action() != PeerAction.NO_ACTION) {
                return result;
            } else {
                return pass();
            }
        }, script.cacheable());
    }

    private @NotNull CheckResult checkShouldBanLegacy(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull Downloader downloader, PipelineTask<?> task) {
        var abilityObject = btnNetwork.getAbilities().get(BtnAbilityRules.class);
        if (abilityObject == null) {
            return pass();
        }
        BtnAbilityRules ruleAbility = (BtnAbilityRules) abilityObject;
        BtnRulesetParsed rule = ruleAbility.getBtnRule();
        if (rule == null) {
            return pass();
        }
        if (isHandShaking(peer)) {
            return handshaking();
        }
        return getCache().readCacheButWritePassOnly(this, "btn-ban-peer-" + peer.getCacheKey(), () -> {
            List<CheckResult> results = new ArrayList<>();
            if (rule.getPeerIdRules() != null) {
                results.add(checkPeerIdRule(rule, torrent, peer));
            }
            if (rule.getClientNameRules() != null) {
                results.add(checkClientNameRule(rule, torrent, peer));
            }
            if (rule.getIpRules() != null) {
                results.add(checkIpRule(rule, torrent, peer));
            }
            if (rule.getPortRules() != null) {
                results.add(checkPortRule(rule, torrent, peer));
            }

            CheckResult finalResult = pass();
            for (CheckResult result : results) {
                if (result != null && result.action() == PeerAction.SKIP) {
                    return result;
                }
                if (result != null && result.action() == PeerAction.BAN) {
                    finalResult = result;
                }
            }
            return finalResult;
        }, true);
    }

    private CheckResult checkPortRule(BtnRulesetParsed rule, Torrent torrent, Peer peer) {
        for (String category : rule.getPortRules().keySet()) {
            RuleMatchResult matchResult = RuleParser.matchRule(rule.getPortRules().get(category), Integer.toString(peer.getPeerAddress().getPort()));
            if (matchResult.hit()) {
                return new CheckResult(getClass(), PeerAction.BAN, banDuration,
                        new TranslationComponent(Lang.BTN_BTN_RULE, category, matchResult.rule().matcherName()),
                        new TranslationComponent(Lang.MODULE_BTN_BAN, "Port", category, matchResult.rule().matcherName()),
                        StructuredData.create()
                                .add("type", "port")
                                .add("category", category)
                                .add("rule", matchResult.rule().metadata()));
            }
        }
        return null;
    }

    private CheckResult checkPortRuleException(LegacyBtnExceptionRuleParsed rule, Torrent torrent, Peer peer) {
        for (String category : rule.getPortRules().keySet()) {
            RuleMatchResult matchResult = RuleParser.matchRule(rule.getPortRules().get(category), Integer.toString(peer.getPeerAddress().getPort()));
            if (matchResult.hit()) {
                return new CheckResult(getClass(), PeerAction.SKIP, banDuration,
                        new TranslationComponent(Lang.BTN_BTN_RULE, category, matchResult.rule().matcherName()),
                        new TranslationComponent(Lang.MODULE_BTN_BAN, "Port", category, matchResult.rule().matcherName()),
                        StructuredData.create()
                                .add("type", "portException")
                                .add("category", category)
                                .add("rule", matchResult.rule().metadata()));
            }
        }
        return null;
    }

    @Nullable
    private CheckResult checkClientNameRule(BtnRulesetParsed rule, Torrent torrent, Peer peer) {
        for (String category : rule.getClientNameRules().keySet()) {
            List<Rule> rules = rule.getClientNameRules().get(category);
            RuleMatchResult matchResult = RuleParser.matchRule(rules, peer.getClientName());
            if (matchResult.hit()) {
                return new CheckResult(getClass(), PeerAction.BAN, banDuration,
                        new TranslationComponent(Lang.BTN_BTN_RULE, category, matchResult.rule().matcherName()),
                        new TranslationComponent(Lang.MODULE_BTN_BAN, "ClientName", category, matchResult.rule().matcherName()),
                        StructuredData.create()
                                .add("type", "clientName")
                                .add("category", category)
                                .add("rule", matchResult.rule().metadata()));
            }
        }
        return null;
    }

    @Nullable
    private CheckResult checkClientNameRuleException(LegacyBtnExceptionRuleParsed rule, Torrent torrent, Peer peer) {
        for (String category : rule.getClientNameRules().keySet()) {
            List<Rule> rules = rule.getClientNameRules().get(category);
            RuleMatchResult matchResult = RuleParser.matchRule(rules, peer.getClientName());
            if (matchResult.hit()) {
                return new CheckResult(getClass(), PeerAction.SKIP, banDuration,
                        new TranslationComponent(Lang.BTN_BTN_RULE, category, matchResult.rule().matcherName()),
                        new TranslationComponent(Lang.MODULE_BTN_BAN, "ClientName", category, matchResult.rule().matcherName()),
                        StructuredData.create()
                                .add("type", "clientNameException")
                                .add("category", category)
                                .add("rule", matchResult.rule().metadata()));
            }
        }
        return null;
    }

    @Nullable
    private CheckResult checkPeerIdRule(BtnRulesetParsed rule, Torrent torrent, Peer peer) {
        for (String category : rule.getPeerIdRules().keySet()) {
            List<Rule> rules = rule.getPeerIdRules().get(category);
            RuleMatchResult matchResult = RuleParser.matchRule(rules, peer.getPeerId());
            if (matchResult.hit()) {
                return new CheckResult(getClass(), PeerAction.BAN, banDuration,
                        new TranslationComponent(Lang.BTN_BTN_RULE, category, matchResult.rule().matcherName()),
                        new TranslationComponent(Lang.MODULE_BTN_BAN, "PeerId", category, matchResult.rule().matcherName()),
                        StructuredData.create()
                                .add("type", "peerId")
                                .add("category", category)
                                .add("rule", matchResult.rule().metadata()));
            }
        }
        return null;
    }

    @Nullable
    private CheckResult checkPeerIdRuleException(LegacyBtnExceptionRuleParsed rule, Torrent torrent, Peer peer) {
        for (String category : rule.getPeerIdRules().keySet()) {
            List<Rule> rules = rule.getPeerIdRules().get(category);
            RuleMatchResult matchResult = RuleParser.matchRule(rules, peer.getPeerId());
            if (matchResult.hit()) {
                return new CheckResult(getClass(), PeerAction.SKIP, banDuration,
                        new TranslationComponent(Lang.BTN_BTN_RULE, category, matchResult.rule().matcherName()),
                        new TranslationComponent(Lang.MODULE_BTN_BAN, "PeerId", category, matchResult.rule().matcherName()),
                        StructuredData.create()
                                .add("type", "peerIdException")
                                .add("category", category)
                                .add("rule", matchResult.rule().metadata()));
            }
        }
        return null;
    }

    @Nullable
    private CheckResult checkIpRule(BtnRulesetParsed rule, @NotNull Torrent torrent, @NotNull Peer peer) {
        IPAddress pa = peer.getPeerAddress().getAddress();
        if (pa == null) return null;
        if (pa.isIPv4Convertible()) {
            pa = pa.toIPv4();
        }
        for (String category : rule.getIpRules().keySet()) {
            var ipMatcher = rule.getIpRules().get(category);
            MatchResult matchResult = ipMatcher.match(pa.toString());
            if (matchResult.result() == MatchResultEnum.TRUE) {
                return new CheckResult(getClass(), PeerAction.BAN, banDuration,
                        new TranslationComponent(Lang.BTN_BTN_RULE, category, category),
                        new TranslationComponent(Lang.MODULE_BTN_BAN, "IP", category, pa.toString()),
                        StructuredData.create()
                                .add("type", "ip")
                                .add("category", category));
            }
        }
        return null;
    }

    @Nullable
    private CheckResult checkIpRuleException(LegacyBtnExceptionRuleParsed rule, @NotNull Torrent torrent, @NotNull Peer peer) {
        IPAddress pa = peer.getPeerAddress().getAddress();
        if (pa == null) return null;
        if (pa.isIPv4Convertible()) {
            pa = pa.toIPv4();
        }
        for (String category : rule.getIpRules().keySet()) {
            RuleMatchResult matchResult = RuleParser.matchRule(rule.getIpRules().get(category), pa.toString());
            if (matchResult.hit()) {
                return new CheckResult(getClass(), PeerAction.SKIP, banDuration,
                        new TranslationComponent(Lang.BTN_BTN_RULE, category, matchResult.rule().matcherIdentifier()),
                        new TranslationComponent(Lang.MODULE_BTN_BAN, "IP", category, pa.toString()),
                        StructuredData.create()
                                .add("type", "ipException")
                                .add("category", category)
                                .add("rule", matchResult.rule().metadata()));
            }
        }
        return null;
    }

    @Subscribe
    public void onRuleUpdate(BtnRuleUpdateEvent event) {
        var allowListAbilityModule = btnNetwork.getAbilities().get(BtnAbilityIPAllowList.class);
        if (allowListAbilityModule == null) return;
        BtnAbilityIPAllowList allowList = (BtnAbilityIPAllowList) allowListAbilityModule;
        List<IPAddress> pendingUnban = new ArrayList<>();
        banList.forEach((ipAddress, _) -> {
            var result = allowList.getIpMatcher().match(ipAddress.toCompressedString());
            if (result.result() == MatchResultEnum.TRUE) {
                pendingUnban.add(ipAddress);
            }
        });
        pendingUnban.forEach(ip -> banList.remove(ip));
    }

}
