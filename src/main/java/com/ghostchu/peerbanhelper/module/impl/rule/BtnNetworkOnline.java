package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.btn.BtnExceptionRuleParsed;
import com.ghostchu.peerbanhelper.btn.BtnNetwork;
import com.ghostchu.peerbanhelper.btn.BtnRulesetParsed;
import com.ghostchu.peerbanhelper.btn.ability.impl.BtnAbilityException;
import com.ghostchu.peerbanhelper.btn.ability.impl.BtnAbilityRules;
import com.ghostchu.peerbanhelper.database.dao.impl.ScriptStorageDao;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.module.AbstractRuleFeatureModule;
import com.ghostchu.peerbanhelper.module.CheckResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.NullUtil;
import com.ghostchu.peerbanhelper.util.SharedObject;
import com.ghostchu.peerbanhelper.util.rule.*;
import com.ghostchu.peerbanhelper.util.scriptengine.CompiledScript;
import com.ghostchu.peerbanhelper.util.scriptengine.ScriptEngine;
import com.ghostchu.peerbanhelper.wrapper.StructuredData;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.ReloadStatus;
import com.ghostchu.simplereloadlib.Reloadable;
import com.googlecode.aviator.exception.TimeoutException;
import inet.ipaddr.IPAddress;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
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
    private ScriptEngine scriptEngine;
    @Autowired
    private ScriptStorageDao scriptStorageDao;
    private boolean allowScript;

    private final ExecutorService parallelService = Executors.newWorkStealingPool();
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
    public @NotNull CheckResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull Downloader downloader) {
        if (btnNetwork == null) {
            return BTN_MANAGER_NOT_INITIALIZED;
        }
        // TODO: 需要重构
        var checkExceptionResult = checkShouldSkip(torrent, peer, downloader);
        if (checkExceptionResult.action() == PeerAction.SKIP) {
            return checkExceptionResult;
        }
        if (allowScript) {
            var scriptResult = checkScript(torrent, peer, downloader, parallelService);
            if (scriptResult.action() != PeerAction.NO_ACTION) {
                return scriptResult;
            }
        }
        return checkShouldBan(torrent, peer, downloader);
    }

    private @NotNull CheckResult checkScript(Torrent torrent, Peer peer, Downloader downloader, ExecutorService ruleExecuteExecutor) {
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

        List<CompletableFuture<CheckResult>> futures = new ArrayList<>();

        for (var kvPair : rule.getScriptRules().entrySet()) {
            futures.add(CompletableFuture.supplyAsync(() -> runExpression(kvPair.getValue(), torrent, peer, downloader), ruleExecuteExecutor));
        }

        CheckResult finalResult = pass();
        for (CompletableFuture<CheckResult> future : futures) {
            CheckResult result = future.join();
            if (result.action() == PeerAction.SKIP) {
                return result; // Early exit on SKIP action
            } else if (result.action() == PeerAction.BAN) {
                finalResult = result;
            }
        }

        return finalResult;
    }

    public @NotNull CheckResult runExpression(CompiledScript script, @NotNull Torrent torrent, @NotNull Peer peer, @NotNull Downloader downloader) {
        return getCache().readCacheButWritePassOnly(this, script.hashCode() + peer.getCacheKey(), () -> {
            CheckResult result;
            try {
                Map<String, Object> env = script.expression().newEnv();
                env.put("torrent", torrent);
                env.put("peer", peer);
                env.put("downloader", downloader);
                env.put("cacheable", new AtomicBoolean(false));
                env.put("server", getServer());
                env.put("moduleInstance", this);
                env.put("btnNetwork", btnNetwork);
                env.put("banDuration", banDuration);
                env.put("kvStorage", SharedObject.SCRIPT_THREAD_SAFE_MAP);
                env.put("persistStorage", scriptStorageDao);
                Object returns;
                if (script.threadSafe()) {
                    returns = script.expression().execute(env);
                } else {
                    synchronized (script.expression()) {
                        returns = script.expression().execute(env);
                    }
                }
                result = scriptEngine.handleResult(script, banDuration, returns);
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

    private @NotNull CheckResult checkShouldSkip(Torrent torrent, Peer peer, Downloader downloader) {
        var abilityObject = btnNetwork.getAbilities().get(BtnAbilityException.class);
        if (abilityObject == null) {
            return pass();
        }
        BtnAbilityException exception = (BtnAbilityException) abilityObject;
        BtnExceptionRuleParsed rule = exception.getBtnExceptionRule();
        if (rule == null) {
            return pass();
        }
        if (isHandShaking(peer)) {
            return handshaking();
        }
        return getCache().readCacheButWritePassOnly(this, "btn-exception-peer-" + peer.getCacheKey(), () -> {
            CheckResult r = null;
            if (rule.getPeerIdRules() != null) {
                r = NullUtil.anyNotNull(r, checkPeerIdRuleException(rule, torrent, peer));
            }
            if (rule.getClientNameRules() != null) {
                r = NullUtil.anyNotNull(r, checkClientNameRuleException(rule, torrent, peer));
            }
            if (rule.getIpRules() != null) {
                r = NullUtil.anyNotNull(r, checkIpRuleException(rule, torrent, peer));
            }
            if (rule.getPortRules() != null) {
                r = NullUtil.anyNotNull(r, checkPortRuleException(rule, torrent, peer));
            }
            if (r == null) {
                return pass();
            }
            return r;
        }, true);
    }

    private @NotNull CheckResult checkShouldBan(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull Downloader downloader) {
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

    private CheckResult checkPortRuleException(BtnExceptionRuleParsed rule, Torrent torrent, Peer peer) {
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
    private CheckResult checkClientNameRuleException(BtnExceptionRuleParsed rule, Torrent torrent, Peer peer) {
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
    private CheckResult checkPeerIdRuleException(BtnExceptionRuleParsed rule, Torrent torrent, Peer peer) {
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
    private CheckResult checkIpRuleException(BtnExceptionRuleParsed rule, @NotNull Torrent torrent, @NotNull Peer peer) {
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
}
