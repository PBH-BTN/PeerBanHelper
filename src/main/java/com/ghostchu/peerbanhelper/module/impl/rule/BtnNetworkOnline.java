package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.btn.BtnExceptionRuleParsed;
import com.ghostchu.peerbanhelper.btn.BtnNetwork;
import com.ghostchu.peerbanhelper.btn.BtnRuleParsed;
import com.ghostchu.peerbanhelper.btn.ability.BtnAbility;
import com.ghostchu.peerbanhelper.btn.ability.BtnAbilityException;
import com.ghostchu.peerbanhelper.btn.ability.BtnAbilityRules;
import com.ghostchu.peerbanhelper.database.dao.impl.ScriptStorageDao;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.module.AbstractRuleFeatureModule;
import com.ghostchu.peerbanhelper.module.CheckResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.scriptengine.CompiledScript;
import com.ghostchu.peerbanhelper.scriptengine.ScriptEngine;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.NullUtil;
import com.ghostchu.peerbanhelper.util.SharedObject;
import com.ghostchu.peerbanhelper.util.context.IgnoreScan;
import com.ghostchu.peerbanhelper.util.rule.*;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.ReloadStatus;
import com.ghostchu.simplereloadlib.Reloadable;
import com.googlecode.aviator.exception.TimeoutException;
import inet.ipaddr.IPAddress;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;
import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
@Component
@IgnoreScan
public class BtnNetworkOnline extends AbstractRuleFeatureModule implements Reloadable {
    private final CheckResult BTN_MANAGER_NOT_INITIALIZED = new CheckResult(getClass(), PeerAction.NO_ACTION, 0, new TranslationComponent(Lang.GENERAL_NA), new TranslationComponent("BtnManager not initialized"));
    private long banDuration;
    @Autowired
    private JavalinWebContainer javalinWebContainer;
    @Autowired(required = false)
    private BtnNetwork btnNetwork;
    @Autowired
    private ScriptEngine scriptEngine;
    @Autowired
    private ScriptStorageDao scriptStorageDao;
    private boolean allowScript;


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

    /**
     * Retrieves and returns the status of the BTN (BitTorrent Network) service.
     *
     * This method generates a comprehensive status report about the BTN network configuration,
     * including configuration success, application details, and ability statuses. If the BTN
     * network is not initialized, it returns a failure response indicating a restart is required.
     *
     * @param context The Javalin request context used for handling the API request and localization
     *
     * @apiNote The method populates a JSON response with the following key information:
     * - Configuration success status
     * - Configuration result message
     * - Application ID and partially masked secret
     * - List of BTN abilities with their current status
     * - Configuration URL
     *
     * @see StdResp
     * @see BtnNetwork
     */
    private void status(Context context) {
        Map<String, Object> info = new HashMap<>();
        if (btnNetwork == null) {
            info.put("configSuccess", false);
            info.put("appId", "N/A");
            info.put("appSecret", "N/A");
            info.put("abilities", Collections.emptyList());
            info.put("configUrl", tl(locale(context), Lang.BTN_SERVICES_NEED_RESTART));
            context.json(new StdResp(false, tl(locale(context), Lang.BTN_NOT_ENABLE_AND_REQUIRE_RESTART), null));
            return;
        }

        info.put("configSuccess", btnNetwork.getConfigSuccess());
        info.put("configResult", btnNetwork.getConfigResult() == null ? null : tl(locale(context), btnNetwork.getConfigResult()));
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
        this.allowScript = getConfig().getBoolean("allow-script-execute");
        getCache().invalidateAll();
    }

    @Override
    public boolean isThreadSafe() {
        return true;
    }

    /**
     * Determines whether a peer should be banned based on a series of rule checks.
     *
     * This method performs a comprehensive evaluation of a peer to decide if it should be banned,
     * following a multi-stage checking process. It first checks for exception rules, then optionally
     * executes custom scripts, and finally applies standard banning rules.
     *
     * @param torrent The torrent associated with the peer
     * @param peer The peer being evaluated for potential banning
     * @param downloader The downloader context
     * @param ruleExecuteExecutor The executor service for running rule checks
     * @return A {@code CheckResult} indicating the action to take (skip, ban, or no action)
     * @throws NullPointerException if any input parameter is null
     */
    @Override
    public @NotNull CheckResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull Downloader downloader, @NotNull ExecutorService ruleExecuteExecutor) {
        if (btnNetwork == null) {
            return BTN_MANAGER_NOT_INITIALIZED;
        }
        // TODO: 需要重构
        var checkExceptionResult = checkShouldSkip(torrent, peer, downloader, ruleExecuteExecutor);
        if (checkExceptionResult.action() == PeerAction.SKIP) {
            return checkExceptionResult;
        }
        if(allowScript) {
            var scriptResult = checkScript(torrent, peer, downloader, ruleExecuteExecutor);
            if (scriptResult.action() != PeerAction.NO_ACTION) {
                return scriptResult;
            }
        }
        return checkShouldBan(torrent, peer, downloader, ruleExecuteExecutor);
    }

    /**
     * Checks the peer against BTN network script rules to determine if it should be banned, skipped, or passed.
     *
     * @param torrent The torrent being downloaded
     * @param peer The peer being evaluated
     * @param downloader The current downloader instance
     * @param ruleExecuteExecutor The executor service for rule execution
     * @return A {@code CheckResult} indicating the action to take for the peer (pass, ban, or skip)
     *
     * @implNote This method:
     * - Retrieves BTN ability rules
     * - Checks if script rules are defined
     * - Handles peers in handshaking state
     * - Executes script rules concurrently using virtual threads
     * - Prioritizes SKIP actions over BAN actions
     * - Logs any script execution errors
     */
    private @NotNull CheckResult checkScript(Torrent torrent, Peer peer, Downloader downloader, ExecutorService ruleExecuteExecutor) {
        var abilityObject = btnNetwork.getAbilities().get(BtnAbilityRules.class);
        if (abilityObject == null) {
            return pass();
        }
        BtnAbilityRules exception = (BtnAbilityRules) abilityObject;
        BtnRuleParsed rule = exception.getBtnRule();
        if (rule == null) {
            return pass();
        }
        if (isHandShaking(peer)) {
            return handshaking();
        }

        List<Future<CheckResult>> futures = new ArrayList<>();
        try (ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor()) {
            for (var kvPair : rule.getScriptRules().entrySet()) {
                futures.add(exec.submit(() -> runExpression(kvPair.getValue(), torrent, peer, downloader, ruleExecuteExecutor)));
            }
        }

        CheckResult finalResult = pass();
        for (Future<CheckResult> future : futures) {
            try {
                CheckResult result = future.get();
                if (result.action() == PeerAction.SKIP) {
                    return result; // Early exit on SKIP action
                } else if (result.action() == PeerAction.BAN) {
                    finalResult = result;
                }
            } catch (InterruptedException | ExecutionException e) {
                log.error("Error executing script, skipping", e);
            }
        }

        return finalResult;
    }

    public @NotNull CheckResult runExpression(CompiledScript script, @NotNull Torrent torrent, @NotNull Peer peer, @NotNull Downloader downloader, @NotNull ExecutorService ruleExecuteExecutor) {
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

    /**
     * Checks if a peer should be skipped based on exception rules defined in the BTN network abilities.
     *
     * This method evaluates whether a peer meets any exception criteria that would prevent it from being banned.
     * It checks various rules including peer ID, client name, IP address, and port against predefined exception rules.
     *
     * @param torrent The torrent associated with the peer
     * @param peer The peer to be evaluated for skipping
     * @param downloader The downloader context (unused in this method)
     * @param ruleExecuteExecutor The executor service for running rule evaluation
     * @return A {@code CheckResult} indicating whether the peer should be skipped:
     *         - Returns {@code pass()} if no exception rules are defined or no rules match
     *         - Returns {@code handshaking()} if the peer is currently in a handshake state
     *         - Returns the specific exception rule result if any rule matches
     */
    private @NotNull CheckResult checkShouldSkip(Torrent torrent, Peer peer, Downloader downloader, ExecutorService ruleExecuteExecutor) {
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
                r = NullUtil.anyNotNull(r, checkPeerIdRuleException(rule, torrent, peer, ruleExecuteExecutor));
            }
            if (rule.getClientNameRules() != null) {
                r = NullUtil.anyNotNull(r, checkClientNameRuleException(rule, torrent, peer, ruleExecuteExecutor));
            }
            if (rule.getIpRules() != null) {
                r = NullUtil.anyNotNull(r, checkIpRuleException(rule, torrent, peer, ruleExecuteExecutor));
            }
            if (rule.getPortRules() != null) {
                r = NullUtil.anyNotNull(r, checkPortRuleException(rule, torrent, peer, ruleExecuteExecutor));
            }
            if (r == null) {
                return pass();
            }
            return r;
        }, true);
    }

    /**
     * Checks whether a peer should be banned based on predefined BTN (BitTorrent Network) rules.
     *
     * This method evaluates a peer against multiple rule categories including peer ID, client name,
     * IP address, and port. It uses a caching mechanism to optimize repeated checks.
     *
     * @param torrent The torrent being downloaded
     * @param peer The peer being evaluated for potential banning
     * @param downloader The current downloader instance
     * @param ruleExecuteExecutor Executor service for running rule evaluation tasks
     * @return A {@code CheckResult} indicating the action to take (pass, ban, or skip)
     *         - Returns {@code pass()} if no rules are defined or abilities are not available
     *         - Returns {@code handshaking()} if the peer is currently in handshake state
     *         - Returns the most restrictive result from rule evaluations
     */
    private @NotNull CheckResult checkShouldBan(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull Downloader downloader, @NotNull ExecutorService ruleExecuteExecutor) {
        var abilityObject = btnNetwork.getAbilities().get(BtnAbilityRules.class);
        if (abilityObject == null) {
            return pass();
        }
        BtnAbilityRules ruleAbility = (BtnAbilityRules) abilityObject;
        BtnRuleParsed rule = ruleAbility.getBtnRule();
        if (rule == null) {
            return pass();
        }
        if (isHandShaking(peer)) {
            return handshaking();
        }
        return getCache().readCacheButWritePassOnly(this, "btn-ban-peer-" + peer.getCacheKey(), () -> {
            List<CheckResult> results = new ArrayList<>();
            if (rule.getPeerIdRules() != null) {
                results.add(checkPeerIdRule(rule, torrent, peer, ruleExecuteExecutor));
            }
            if (rule.getClientNameRules() != null) {
                results.add(checkClientNameRule(rule, torrent, peer, ruleExecuteExecutor));
            }
            if (rule.getIpRules() != null) {
                results.add(checkIpRule(rule, torrent, peer, ruleExecuteExecutor));
            }
            if (rule.getPortRules() != null) {
                results.add(checkPortRule(rule, torrent, peer, ruleExecuteExecutor));
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

    private CheckResult checkPortRule(BtnRuleParsed rule, Torrent torrent, Peer peer, ExecutorService ruleExecuteExecutor) {
        for (String category : rule.getPortRules().keySet()) {
            RuleMatchResult matchResult = RuleParser.matchRule(rule.getPortRules().get(category), Integer.toString(peer.getPeerAddress().getPort()));
            if (matchResult.hit()) {
                return new CheckResult(getClass(), PeerAction.BAN, banDuration, new TranslationComponent(Lang.BTN_BTN_RULE, category, matchResult.rule().matcherIdentifier()), new TranslationComponent(Lang.MODULE_BTN_BAN, "Port", category, matchResult.rule().toString()));
            }
        }
        return null;
    }

    private CheckResult checkPortRuleException(BtnExceptionRuleParsed rule, Torrent torrent, Peer peer, ExecutorService ruleExecuteExecutor) {
        for (String category : rule.getPortRules().keySet()) {
            RuleMatchResult matchResult = RuleParser.matchRule(rule.getPortRules().get(category), Integer.toString(peer.getPeerAddress().getPort()));
            if (matchResult.hit()) {
                return new CheckResult(getClass(), PeerAction.SKIP, banDuration, new TranslationComponent(Lang.BTN_BTN_RULE, category, matchResult.rule().matcherIdentifier()), new TranslationComponent(Lang.MODULE_BTN_BAN, "Port", category, matchResult.rule().toString()));
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
    private CheckResult checkClientNameRuleException(BtnExceptionRuleParsed rule, Torrent torrent, Peer peer, ExecutorService ruleExecuteExecutor) {
        for (String category : rule.getClientNameRules().keySet()) {
            List<Rule> rules = rule.getClientNameRules().get(category);
            RuleMatchResult matchResult = RuleParser.matchRule(rules, peer.getClientName());
            if (matchResult.hit()) {
                return new CheckResult(getClass(), PeerAction.SKIP, banDuration, new TranslationComponent(Lang.BTN_BTN_RULE, category, matchResult.rule().matcherIdentifier()), new TranslationComponent(Lang.MODULE_BTN_BAN, "ClientName", category, matchResult.rule().toString()));
            }
        }
        return null;
    }

    @Nullable
    private CheckResult checkPeerIdRule(BtnRuleParsed rule, Torrent torrent, Peer peer, ExecutorService ruleExecuteExecutor) {
        for (String category : rule.getPeerIdRules().keySet()) {
            List<Rule> rules = rule.getPeerIdRules().get(category);
            RuleMatchResult matchResult = RuleParser.matchRule(rules, peer.getPeerId());
            if (matchResult.hit()) {
                return new CheckResult(getClass(), PeerAction.BAN, banDuration, new TranslationComponent(Lang.BTN_BTN_RULE, category, matchResult.rule().matcherIdentifier()), new TranslationComponent(Lang.MODULE_BTN_BAN, "PeerId", category, matchResult.rule().toString()));
            }
        }
        return null;
    }

    @Nullable
    private CheckResult checkPeerIdRuleException(BtnExceptionRuleParsed rule, Torrent torrent, Peer peer, ExecutorService ruleExecuteExecutor) {
        for (String category : rule.getPeerIdRules().keySet()) {
            List<Rule> rules = rule.getPeerIdRules().get(category);
            RuleMatchResult matchResult = RuleParser.matchRule(rules, peer.getPeerId());
            if (matchResult.hit()) {
                return new CheckResult(getClass(), PeerAction.SKIP, banDuration, new TranslationComponent(Lang.BTN_BTN_RULE, category, matchResult.rule().matcherIdentifier()), new TranslationComponent(Lang.MODULE_BTN_BAN, "PeerId", category, matchResult.rule().toString()));
            }
        }
        return null;
    }

    /**
     * Checks if a peer's IP address matches any IP rules defined in the BTN rule.
     *
     * @param rule The parsed BTN rule containing IP matching configurations
     * @param torrent The torrent associated with the peer (unused in this method)
     * @param peer The peer being evaluated for IP rule matching
     * @param ruleExecuteExecutor The executor service for rule execution (unused in this method)
     * @return A CheckResult indicating a ban if the IP matches a rule, or null if no match is found
     */
    @Nullable
    private CheckResult checkIpRule(BtnRuleParsed rule, @NotNull Torrent torrent, @NotNull Peer peer, @NotNull ExecutorService ruleExecuteExecutor) {
        IPAddress pa = peer.getPeerAddress().getAddress();
        if (pa == null) return null;
        if (pa.isIPv4Convertible()) {
            pa = pa.toIPv4();
        }
        for (String category : rule.getIpRules().keySet()) {
            var ipMatcher = rule.getIpRules().get(category);
            MatchResult matchResult = ipMatcher.match(pa.toString());
            if (matchResult.result() == MatchResultEnum.TRUE) {
                return new CheckResult(getClass(), PeerAction.BAN, banDuration, new TranslationComponent(Lang.BTN_BTN_RULE, category, category), new TranslationComponent(Lang.MODULE_BTN_BAN, "IP", category, pa.toString()));
            }
        }
        return null;
    }

    @Nullable
    private CheckResult checkIpRuleException(BtnExceptionRuleParsed rule, @NotNull Torrent torrent, @NotNull Peer peer, @NotNull ExecutorService ruleExecuteExecutor) {
        IPAddress pa = peer.getPeerAddress().getAddress();
        if (pa == null) return null;
        if (pa.isIPv4Convertible()) {
            pa = pa.toIPv4();
        }
        for (String category : rule.getIpRules().keySet()) {
            RuleMatchResult matchResult = RuleParser.matchRule(rule.getIpRules().get(category), pa.toString());
            if (matchResult.hit()) {
                return new CheckResult(getClass(), PeerAction.SKIP, banDuration, new TranslationComponent(Lang.BTN_BTN_RULE, category, matchResult.rule().matcherIdentifier()), new TranslationComponent(Lang.MODULE_BTN_BAN, "IP", category, pa.toString()));
            }
        }
        return null;
    }
}
