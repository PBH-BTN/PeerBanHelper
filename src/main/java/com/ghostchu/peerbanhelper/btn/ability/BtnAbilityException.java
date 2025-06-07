package com.ghostchu.peerbanhelper.btn.ability;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.btn.BtnExceptionRule;
import com.ghostchu.peerbanhelper.btn.BtnExceptionRuleParsed;
import com.ghostchu.peerbanhelper.btn.BtnNetwork;
import com.ghostchu.peerbanhelper.event.BtnExceptionRuleUpdateEvent;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.URLUtil;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.util.rule.Rule;
import com.ghostchu.peerbanhelper.util.rule.RuleMatchResult;
import com.ghostchu.peerbanhelper.util.rule.RuleParser;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.github.mizosoft.methanol.MutableRequest;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public final class BtnAbilityException extends AbstractBtnAbility {
    private final BtnNetwork btnNetwork;
    private final long interval;
    private final String endpoint;
    private final long randomInitialDelay;
    private final File btnCacheFile = new File(Main.getDataDirectory(), "btn-exception.cache");
    @Getter
    private BtnExceptionRuleParsed btnExceptionRule;


    public BtnAbilityException(BtnNetwork btnNetwork, JsonObject ability) {
        this.btnNetwork = btnNetwork;
        this.interval = ability.get("interval").getAsLong();
        this.endpoint = ability.get("endpoint").getAsString();
        this.randomInitialDelay = ability.get("random_initial_delay").getAsLong();
        setLastStatus(true, new TranslationComponent(Lang.BTN_STAND_BY));
    }

    private void loadCacheFile() throws IOException {
        if (!btnCacheFile.exists()) {
            if (!btnCacheFile.getParentFile().exists()) {
                btnCacheFile.getParentFile().mkdirs();
            }
            btnCacheFile.createNewFile();
        } else {
            try {
                BtnExceptionRule btnRule = JsonUtil.getGson().fromJson(Files.readString(btnCacheFile.toPath()), BtnExceptionRule.class);
                this.btnExceptionRule = new BtnExceptionRuleParsed(btnRule);
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public String getName() {
        return "BtnAbilityException";
    }

    @Override
    public TranslationComponent getDisplayName() {
        return new TranslationComponent(Lang.BTN_ABILITY_EXCEPTION);
    }

    @Override
    public TranslationComponent getDescription() {
        return new TranslationComponent(Lang.BTN_ABILITY_EXCEPTION_DESCRIPTION);
    }

    @Override
    public void load() {
        try {
            loadCacheFile();
            setLastStatus(true, new TranslationComponent(Lang.BTN_ABILITY_EXCEPTION_LOADED_FROM_CACHE));
        } catch (Exception e) {
            log.error(tlUI(Lang.BTN_ABILITY_EXCEPTION_LOADED_FROM_CACHE_FAILED));
            setLastStatus(false, new TranslationComponent(e.getClass().getName() + ": " + e.getMessage()));
        }
        btnNetwork.getExecuteService().scheduleWithFixedDelay(this::updateRule, ThreadLocalRandom.current().nextLong(randomInitialDelay), interval, TimeUnit.MILLISECONDS);
    }

    private void updateRule() {
        String version;
        if (btnExceptionRule == null || btnExceptionRule.getVersion() == null) {
            version = "initial";
        } else {
            version = btnExceptionRule.getVersion();
        }
        HTTPUtil.retryableSend(
                        btnNetwork.getHttpClient(),
                        MutableRequest.GET(URLUtil.appendUrl(endpoint, Map.of("rev", version))),
                        HttpResponse.BodyHandlers.ofString())
                .thenAccept(r -> {
                    if (r.statusCode() == 204) {
                        setLastStatus(true, new TranslationComponent(Lang.BTN_ABILITY_EXCEPTION_LOADED_FROM_REMOTE, this.btnExceptionRule.getVersion()));
                        return;
                    }
                    if (r.statusCode() != 200) {
                        log.error(tlUI(Lang.BTN_REQUEST_FAILS, r.statusCode() + " - " + r.body()));
                        setLastStatus(false, new TranslationComponent(Lang.BTN_HTTP_ERROR, r.statusCode(), r.body()));
                    } else {
                        try {
                            BtnExceptionRule btr = JsonUtil.getGson().fromJson(r.body(), BtnExceptionRule.class);
                            this.btnExceptionRule = new BtnExceptionRuleParsed(btr);
                            Main.getEventBus().post(new BtnExceptionRuleUpdateEvent());
                            try {
                                Files.writeString(btnCacheFile.toPath(), r.body(), StandardCharsets.UTF_8);
                            } catch (IOException ignored) {
                            }
                            log.info(tlUI(Lang.BTN_ABILITY_EXCEPTION_UPDATE_RULES_SUCCESSES, this.btnExceptionRule.getVersion()));
                            setLastStatus(true, new TranslationComponent(Lang.BTN_ABILITY_EXCEPTION_LOADED_FROM_REMOTE, this.btnExceptionRule.getVersion()));
                            unbanPeers(btnExceptionRule);
                            btnNetwork.getModuleMatchCache().invalidateAll();
                        } catch (JsonSyntaxException e) {
                            setLastStatus(false, new TranslationComponent("JsonSyntaxException: " + r.statusCode() + " - " + r.body()));
                            log.error("Unable to parse BtnExceptionRule as a valid Json object: {}-{}", r.statusCode(), r.body(), e);
                        }
                    }
                })
                .exceptionally((e) -> {
                    log.error(tlUI(Lang.BTN_REQUEST_FAILS), e);
                    setLastStatus(false, new TranslationComponent(Lang.BTN_UNKNOWN_ERROR, e.getClass().getName() + ": " + e.getMessage()));
                    return null;
                });
    }

    private void unbanPeers(BtnExceptionRuleParsed btr) {
        var ipList = btr.getIpRules().values().stream().flatMap(Collection::stream)
                .toList();
        var clientNameList = btr.getClientNameRules().values().stream().flatMap(Collection::stream)
                .toList();
        var peerIdList = btr.getPeerIdRules().values().stream().flatMap(Collection::stream)
                .toList();
        var portList = btr.getPortRules().values().stream().flatMap(Collection::stream)
                .toList();
        int ct = 0;
        ct += unbanIps(ipList);
        ct += unbanClientName(clientNameList);
        ct += unbanPeerId(peerIdList);
        ct += unbanPort(portList);
        log.info(tlUI(Lang.BTN_ABILITY_EXCEPTION_UNBANNED_PEERS, ct));
    }

    private int unbanIps(List<Rule> rules) {
        int ct = 0;
        for (PeerAddress pa : btnNetwork.getServer().getBannedPeers().keySet()) {
            RuleMatchResult matchResult = RuleParser.matchRule(rules, pa.toString());
            if (matchResult.hit()) {
                btnNetwork.getServer().scheduleUnBanPeer(pa);
                ct++;
            }
        }
        return ct;
    }

    private int unbanClientName(List<Rule> rules) {
        int ct = 0;
        for (var entry : btnNetwork.getServer().getBannedPeers().entrySet()) {
            RuleMatchResult matchResult = RuleParser.matchRule(rules, entry.getValue().getPeer().getClientName());
            if (matchResult.hit()) {
                btnNetwork.getServer().scheduleUnBanPeer(entry.getKey());
                ct++;
            }
        }
        return ct;
    }

    private int unbanPeerId(List<Rule> rules) {
        int ct = 0;
        for (var entry : btnNetwork.getServer().getBannedPeers().entrySet()) {
            RuleMatchResult matchResult = RuleParser.matchRule(rules, entry.getValue().getPeer().getId());
            if (matchResult.hit()) {
                btnNetwork.getServer().scheduleUnBanPeer(entry.getKey());
                ct++;
            }
        }
        return ct;
    }

    private int unbanPort(List<Rule> rules) {
        int ct = 0;
        for (var entry : btnNetwork.getServer().getBannedPeers().entrySet()) {
            RuleMatchResult matchResult = RuleParser.matchRule(rules, Integer.toString(entry.getValue().getPeer().getAddress().getPort()));
            if (matchResult.hit()) {
                btnNetwork.getServer().scheduleUnBanPeer(entry.getKey());
                ct++;
            }
        }
        return ct;
    }

    @Override
    public void unload() {

    }
}
