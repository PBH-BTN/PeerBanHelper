package com.ghostchu.peerbanhelper.btn.ability;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.btn.BtnNetwork;
import com.ghostchu.peerbanhelper.btn.BtnRule;
import com.ghostchu.peerbanhelper.btn.BtnRuleParsed;
import com.ghostchu.peerbanhelper.event.BtnRuleUpdateEvent;
import com.ghostchu.peerbanhelper.scriptengine.ScriptEngine;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.URLUtil;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.util.rule.matcher.IPMatcher;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public final class BtnAbilityRules extends AbstractBtnAbility {
    private final BtnNetwork btnNetwork;
    private final long interval;
    private final String endpoint;
    private final long randomInitialDelay;
    private final File btnCacheFile = new File(Main.getDataDirectory(), "btn.cache");
    private final ScriptEngine scriptEngine;
    private final boolean scriptExecute;
    @Getter
    private BtnRuleParsed btnRule;


    public BtnAbilityRules(BtnNetwork btnNetwork, ScriptEngine scriptEngine, JsonObject ability, boolean scriptExecute) {
        this.btnNetwork = btnNetwork;
        this.scriptEngine = scriptEngine;
        this.interval = ability.get("interval").getAsLong();
        this.endpoint = ability.get("endpoint").getAsString();
        this.randomInitialDelay = ability.get("random_initial_delay").getAsLong();
        this.scriptExecute = scriptExecute;
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
                BtnRule btnRule = JsonUtil.getGson().fromJson(Files.readString(btnCacheFile.toPath()), BtnRule.class);
                this.btnRule = new BtnRuleParsed(scriptEngine, btnRule, scriptExecute);
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public String getName() {
        return "BtnAbilityRules";
    }

    @Override
    public TranslationComponent getDisplayName() {
        return new TranslationComponent(Lang.BTN_ABILITY_RULES);
    }

    @Override
    public TranslationComponent getDescription() {
        if(btnRule == null){
            return new TranslationComponent(Lang.BTN_ABILITY_RULES_DESCRIPTION, "N/A", 0, 0, 0, 0, 0);
        }
        return new TranslationComponent(Lang.BTN_ABILITY_RULES_DESCRIPTION,
                btnRule.getVersion(),
                btnRule.size(),
                btnRule.getIpRules().values().stream().mapToLong(IPMatcher::size).sum(),
                btnRule.getPeerIdRules().values().stream().mapToLong(List::size).sum(),
                btnRule.getClientNameRules().values().stream().mapToLong(List::size).sum(),
                btnRule.getPortRules().values().stream().mapToLong(List::size).sum(),
                btnRule.getScriptRules().values().size());
    }

    @Override
    public void load() {
        try {
            loadCacheFile();
            setLastStatus(true, new TranslationComponent(Lang.BTN_RULES_LOADED_FROM_CACHE));
        } catch (Exception e) {
            log.error(tlUI(Lang.BTN_RULES_LOAD_FROM_CACHE_FAILED));
            setLastStatus(false, new TranslationComponent(e.getClass().getName() + ": " + e.getMessage()));
        }
        btnNetwork.getExecuteService().scheduleWithFixedDelay(this::updateRule, ThreadLocalRandom.current().nextLong(randomInitialDelay), interval, TimeUnit.MILLISECONDS);
    }

    private void updateRule() {
        String version;
        if (btnRule == null || btnRule.getVersion() == null) {
            version = "initial";
        } else {
            version = btnRule.getVersion();
        }
        HTTPUtil.retryableSend(
                        btnNetwork.getHttpClient(),
                        MutableRequest.GET(URLUtil.appendUrl(endpoint, Map.of("rev", version))),
                        HttpResponse.BodyHandlers.ofString())
                .thenAccept(r -> {
                    if (r.statusCode() == 204) {
                        setLastStatus(true, new TranslationComponent(Lang.BTN_RULES_LOADED_FROM_REMOTE, this.btnRule.getVersion()));
                        return;
                    }
                    if (r.statusCode() != 200) {
                        log.error(tlUI(Lang.BTN_REQUEST_FAILS, r.statusCode() + " - " + r.body()));
                        setLastStatus(false, new TranslationComponent(Lang.BTN_HTTP_ERROR, r.statusCode(), r.body()));
                    } else {
                        try {
                            BtnRule btr = JsonUtil.getGson().fromJson(r.body(), BtnRule.class);
                            this.btnRule = new BtnRuleParsed(scriptEngine, btr, scriptExecute);
                            Main.getEventBus().post(new BtnRuleUpdateEvent());
                            try {
                                Files.writeString(btnCacheFile.toPath(), r.body(), StandardCharsets.UTF_8);
                            } catch (IOException ignored) {
                            }
                            log.info(tlUI(Lang.BTN_UPDATE_RULES_SUCCESSES, this.btnRule.getVersion()));
                            setLastStatus(true, new TranslationComponent(Lang.BTN_RULES_LOADED_FROM_REMOTE, this.btnRule.getVersion()));
                            btnNetwork.getModuleMatchCache().invalidateAll();
                        } catch (JsonSyntaxException e) {
                            setLastStatus(false, new TranslationComponent("JsonSyntaxException: " + r.statusCode() + " - " + r.body()));
                            log.error("Unable to parse BtnRule as a valid Json object: {}-{}", r.statusCode(), r.body(), e);
                        }
                    }
                })
                .exceptionally((e) -> {
                    log.error(tlUI(Lang.BTN_REQUEST_FAILS), e);
                    setLastStatus(false, new TranslationComponent(Lang.BTN_UNKNOWN_ERROR, e.getClass().getName() + ": " + e.getMessage()));
                    return null;
                });
    }

    @Override
    public void unload() {

    }
}
