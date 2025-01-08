package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.lab.Experiments;
import com.ghostchu.peerbanhelper.lab.Laboratory;
import com.ghostchu.peerbanhelper.module.AbstractRuleFeatureModule;
import com.ghostchu.peerbanhelper.module.CheckResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.context.IgnoreScan;
import com.ghostchu.peerbanhelper.util.dns.DNSLookup;
import com.ghostchu.peerbanhelper.util.rule.Rule;
import com.ghostchu.peerbanhelper.util.rule.RuleMatchResult;
import com.ghostchu.peerbanhelper.util.rule.RuleParser;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

@Component
@IgnoreScan
public class PTRBlacklist extends AbstractRuleFeatureModule implements Reloadable {
    private List<Rule> ptrRules;
    @Autowired
    private JavalinWebContainer webContainer;
    @Autowired
    private DNSLookup dnsLookup;
    private long banDuration;
    @Autowired
    private Laboratory laboratory;

    /**
     * Returns the name of the PTR Blacklist module.
     *
     * @return A string representing the module's human-readable name, which is "PTR Blacklist"
     */
    @Override
    public @NotNull String getName() {
        return "PTR Blacklist";
    }

    /**
     * Returns the configuration name for the PTR Blacklist module.
     *
     * @return A string representing the unique configuration identifier for this module
     */
    @Override
    public @NotNull String getConfigName() {
        return "ptr-blacklist";
    }


    /**
     * Indicates whether the PTR Blacklist module is configurable.
     *
     * @return {@code true} to signify that the module supports configuration options
     */
    @Override
    public boolean isConfigurable() {
        return true;
    }


    /**
     * Enables the PTR Blacklist module by performing initial configuration and setting up web API access.
     *
     * This method is called when the module is being activated and performs the following tasks:
     * 1. Reloads the module's configuration using {@link #reloadConfig()}
     * 2. Registers a web API endpoint for retrieving PTR rules
     * 3. Registers the module with the reload manager for dynamic configuration updates
     *
     * The web API endpoint is accessible at "/api/modules/ptr-blacklist" and requires USER_READ permissions.
     *
     * @see #reloadConfig()
     * @see Main#getReloadManager()
     */
    @Override
    public void onEnable() {
        reloadConfig();
        webContainer.javalin()
                .get("/api/modules/" + getConfigName(), this::handleWebAPI, Role.USER_READ);
        Main.getReloadManager().register(this);
    }

    /**
     * Indicates whether the PTR Blacklist module is thread-safe.
     *
     * @return {@code true} to signify that this module can be safely accessed and used concurrently by multiple threads
     */
    @Override
    public boolean isThreadSafe() {
        return true;
    }

    /**
     * Handles web API requests for retrieving PTR (Pointer) rules.
     *
     * @param ctx The Javalin web context containing the request information
     * @apiNote This method generates a JSON response with the current PTR rules
     *          translated to the specified locale
     */
    private void handleWebAPI(Context ctx) {
        String locale = locale(ctx);
        ctx.json(new StdResp(true, null, Map.of("ptr-rules", ptrRules.stream().map(r -> r.toPrintableText(locale)).toList())));
    }

    /**
     * Unregisters the PTR Blacklist module from the reload manager when the module is being disabled.
     * 
     * This method is called during the module shutdown process and ensures that the module is properly
     * removed from the reload management system, preventing any further reload attempts or interactions.
     */
    @Override
    public void onDisable() {
        Main.getReloadManager().unregister(this);
    }

    /**
     * Reloads the PTR Blacklist module configuration and returns the reload result.
     *
     * @return A {@code ReloadResult} indicating the status of the module reload
     * @throws Exception if an error occurs during module configuration reload
     */
    @Override
    public ReloadResult reloadModule() throws Exception {
        reloadConfig();
        return Reloadable.super.reloadModule();
    }

    /**
     * Reloads the configuration for the PTR Blacklist module.
     *
     * This method updates the ban duration and PTR rules from the configuration file.
     * It also invalidates the entire cache to ensure fresh rule application.
     *
     * @see RuleParser#parse(List)
     */
    public void reloadConfig() {
        this.banDuration = getConfig().getLong("ban-duration", 0);
        this.ptrRules = RuleParser.parse(getConfig().getStringList("ptr-rules"));
        getCache().invalidateAll();
    }

    /**
     * Determines whether a peer should be banned based on its PTR (Pointer) DNS record.
     *
     * This method performs a reverse DNS lookup on the peer's address and checks the result
     * against a predefined set of PTR rules. If a matching rule is found, the peer will be banned.
     *
     * @param torrent The torrent context in which the peer is being evaluated
     * @param peer The peer being checked for potential banning
     * @param downloader The current downloader instance
     * @param ruleExecuteExecutor The executor service for rule processing
     * @return A {@code CheckResult} indicating whether the peer should be banned or not
     *         - If a matching PTR rule is found, returns a ban result with the matched rule
     *         - Otherwise, returns a pass result
     *
     * @see RuleParser
     * @see DNSLookup
     * @see Experiments
     */
    @Override
    public @NotNull CheckResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull Downloader downloader, @NotNull ExecutorService ruleExecuteExecutor) {
        var reverseDnsLookupString = peer.getPeerAddress().getAddress().toReverseDNSLookupString();
        return getCache().readCache(this, reverseDnsLookupString, () -> {
            Optional<String> ptr;
            if (laboratory.isExperimentActivated(Experiments.DNSJAVA.getExperiment())) {
                ptr = dnsLookup.ptr(reverseDnsLookupString).join();
            } else {
                try {
                    ptr = Optional.ofNullable(InetAddress.getByName(peer.getPeerAddress().getIp()).getHostName());
                } catch (UnknownHostException e) {
                    ptr = Optional.empty();
                }
            }
            if (ptr.isPresent()) {
                RuleMatchResult matchResult = RuleParser.matchRule(ptrRules, ptr.get());
                if (matchResult.hit()) {
                    return new CheckResult(getClass(), PeerAction.BAN, banDuration, new TranslationComponent(matchResult.rule().toString()),
                            new TranslationComponent(Lang.MODULE_PTR_MATCH_PTR_RULE, matchResult.rule().toString()));
                }
            }
            return pass();
        }, true);
    }

}
