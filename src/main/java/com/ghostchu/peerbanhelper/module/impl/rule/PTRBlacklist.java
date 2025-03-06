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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@IgnoreScan
public final class PTRBlacklist extends AbstractRuleFeatureModule implements Reloadable {
    private List<Rule> ptrRules;
    @Autowired
    private JavalinWebContainer webContainer;
    @Autowired
    private DNSLookup dnsLookup;
    private long banDuration;
    @Autowired
    private Laboratory laboratory;

    @Override
    public @NotNull String getName() {
        return "PTR Blacklist";
    }

    @Override
    public @NotNull String getConfigName() {
        return "ptr-blacklist";
    }


    @Override
    public boolean isConfigurable() {
        return true;
    }


    @Override
    public void onEnable() {
        reloadConfig();
        webContainer.javalin()
                .get("/api/modules/" + getConfigName(), this::handleWebAPI, Role.USER_READ);
        Main.getReloadManager().register(this);
    }

    @Override
    public boolean isThreadSafe() {
        return true;
    }

    private void handleWebAPI(Context ctx) {
        String locale = locale(ctx);
        ctx.json(new StdResp(true, null, Map.of("ptr-rules", ptrRules.stream().map(r -> r.toPrintableText(locale)).toList())));
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
        this.ptrRules = RuleParser.parse(getConfig().getStringList("ptr-rules"));
        getCache().invalidateAll();
    }

    @Override
    public @NotNull CheckResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull Downloader downloader, @NotNull ExecutorService ruleExecuteExecutor) {
        if (isHandShaking(peer)) {
            return handshaking();
        }
        var reverseDnsLookupString = peer.getPeerAddress().getAddress().toReverseDNSLookupString();
        return getCache().readCache(this, reverseDnsLookupString, () -> {
            Optional<String> ptr;
            if (laboratory.isExperimentActivated(Experiments.DNSJAVA.getExperiment())) {
                try {
                    ptr = dnsLookup.ptr(reverseDnsLookupString).get(3, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    ptr = Optional.empty();
                }
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
                    return new CheckResult(getClass(), PeerAction.BAN, banDuration, matchResult.rule().matcherName(),
                            new TranslationComponent(Lang.MODULE_PTR_MATCH_PTR_RULE, matchResult.rule().matcherName()));
                }
            }
            return pass();
        }, true);
    }

}
