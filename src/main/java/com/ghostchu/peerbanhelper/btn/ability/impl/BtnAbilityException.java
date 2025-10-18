package com.ghostchu.peerbanhelper.btn.ability.impl;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.btn.BtnExceptionRule;
import com.ghostchu.peerbanhelper.btn.BtnExceptionRuleParsed;
import com.ghostchu.peerbanhelper.btn.BtnNetwork;
import com.ghostchu.peerbanhelper.btn.ability.AbstractBtnAbility;
import com.ghostchu.peerbanhelper.event.btn.BtnExceptionRuleUpdateEvent;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.URLUtil;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.util.rule.Rule;
import com.ghostchu.peerbanhelper.util.rule.RuleMatchResult;
import com.ghostchu.peerbanhelper.util.rule.RuleParser;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import inet.ipaddr.IPAddress;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.Response;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
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
        btnNetwork.getScheduler().scheduleWithFixedDelay(this::updateRule, ThreadLocalRandom.current().nextLong(randomInitialDelay), interval, TimeUnit.MILLISECONDS);
    }

    private void updateRule() {
        String version;
        if (btnExceptionRule == null || btnExceptionRule.getVersion() == null) {
            version = "initial";
        } else {
            version = btnExceptionRule.getVersion();
        }

        Request request = new Request.Builder()
                .url(URLUtil.appendUrl(endpoint, Map.of("rev", version)))
                .get()
                .build();

        try (Response response = btnNetwork.getHttpClient().newCall(request).execute()) {
            if (response.code() == 204) {
                setLastStatus(true, new TranslationComponent(Lang.BTN_ABILITY_EXCEPTION_LOADED_FROM_REMOTE, this.btnExceptionRule.getVersion()));
                return;
            }
            if (!response.isSuccessful()) {
                String body = response.body().string();
                log.error(tlUI(Lang.BTN_REQUEST_FAILS, response.code() + " - " + body));
                setLastStatus(false, new TranslationComponent(Lang.BTN_HTTP_ERROR, response.code(), body));
            } else {
                try {
                    String body = response.body().string();
                    BtnExceptionRule btr = JsonUtil.getGson().fromJson(body, BtnExceptionRule.class);
                    this.btnExceptionRule = new BtnExceptionRuleParsed(btr);
                    Main.getEventBus().post(new BtnExceptionRuleUpdateEvent());
                    try {
                        Files.writeString(btnCacheFile.toPath(), body, StandardCharsets.UTF_8);
                    } catch (IOException ignored) {
                    }
                    log.info(tlUI(Lang.BTN_ABILITY_EXCEPTION_UPDATE_RULES_SUCCESSES, this.btnExceptionRule.getVersion()));
                    setLastStatus(true, new TranslationComponent(Lang.BTN_ABILITY_EXCEPTION_LOADED_FROM_REMOTE, this.btnExceptionRule.getVersion()));
                    unbanPeers(btnExceptionRule);
                    btnNetwork.getModuleMatchCache().invalidateAll();
                } catch (JsonSyntaxException e) {
                    String body = response.body().string();
                    setLastStatus(false, new TranslationComponent("JsonSyntaxException: " + response.code() + " - " + body));
                    log.error("Unable to parse BtnExceptionRule as a valid Json object: {}-{}", response.code(), body, e);
                }
            }
        } catch (Exception e) {
            log.error(tlUI(Lang.BTN_REQUEST_FAILS), e);
            setLastStatus(false, new TranslationComponent(Lang.BTN_UNKNOWN_ERROR, e.getClass().getName() + ": " + e.getMessage()));
        }
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
        List<IPAddress> list = new ArrayList<>();
        btnNetwork.getServer().getBanList().forEach((addr, meta) -> {
            RuleMatchResult matchResult = RuleParser.matchRule(rules, addr.toNormalizedString());
            if (matchResult.hit()) {
                list.add(addr);
            }
        });
        list.forEach(addr -> btnNetwork.getServer().scheduleUnBanPeer(addr));
        return list.size();
    }

    private int unbanClientName(List<Rule> rules) {
        List<IPAddress> list = new ArrayList<>();
        btnNetwork.getServer().getBanList().forEach((addr, meta) -> {
            RuleMatchResult matchResult = RuleParser.matchRule(rules, meta.getPeer().getClientName());
            if (matchResult.hit()) {
                list.add(addr);
            }
        });
        list.forEach(addr -> btnNetwork.getServer().scheduleUnBanPeer(addr));

        return list.size();
    }

    private int unbanPeerId(List<Rule> rules) {
        List<IPAddress> list = new ArrayList<>();
        btnNetwork.getServer().getBanList().forEach((addr, meta) -> {
            RuleMatchResult matchResult = RuleParser.matchRule(rules, meta.getPeer().getId());
            if (matchResult.hit()) {
                list.add(addr);
            }
        });
        list.forEach(addr -> btnNetwork.getServer().scheduleUnBanPeer(addr));
        return list.size();
    }

    private int unbanPort(List<Rule> rules) {
        List<IPAddress> list = new ArrayList<>();
        btnNetwork.getServer().getBanList().forEach((addr, meta) -> {
            RuleMatchResult matchResult = RuleParser.matchRule(rules, Integer.toString(meta.getPeer().getAddress().getPort()));
            if (matchResult.hit()) {
                list.add(addr);
            }
        });
        list.forEach(addr -> btnNetwork.getServer().scheduleUnBanPeer(addr));
        return list.size();
    }

    @Override
    public void unload() {

    }
}
