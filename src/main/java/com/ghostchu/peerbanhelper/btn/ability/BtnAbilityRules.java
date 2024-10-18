package com.ghostchu.peerbanhelper.btn.ability;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.btn.BtnNetwork;
import com.ghostchu.peerbanhelper.btn.BtnRule;
import com.ghostchu.peerbanhelper.btn.BtnRuleParsed;
import com.ghostchu.peerbanhelper.event.BtnRuleUpdateEvent;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.URLUtil;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public class BtnAbilityRules extends AbstractBtnAbility {
    private final BtnNetwork btnNetwork;
    private final long interval;
    private final String endpoint;
    private final long randomInitialDelay;
    private final File btnCacheFile = new File(Main.getDataDirectory(), "btn.cache");
    @Getter
    private BtnRuleParsed btnRule;

    public BtnAbilityRules(BtnNetwork btnNetwork, JsonObject ability) {
        this.btnNetwork = btnNetwork;
        this.interval = ability.get("interval").getAsLong();
        this.endpoint = ability.get("endpoint").getAsString();
        this.randomInitialDelay = ability.get("random_initial_delay").getAsLong();
        setLastStatus(true, "Stand by");
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
                this.btnRule = new BtnRuleParsed(btnRule);
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public void load() {
        try {
            loadCacheFile();
            setLastStatus(true, "Loaded from disk cache");
        } catch (Exception e) {
            log.error("Unable to load cached BTN rules into memory");
            setLastStatus(false, e.getClass().getName() + ": " + e.getMessage());
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
        HTTPUtil.retryableSend(btnNetwork.getHttpClient(),
                        new Request.Builder().url(URLUtil.appendUrl(endpoint, Map.of("rev", version))).build())
                .thenAccept(r -> {
                    if (r.code() == 204) {
                        setLastStatus(true, "Not modified");
                        return;
                    }
                    if (r.code() != 200) {
                        try {
                            String body = r.body().string();
                            log.error(tlUI(Lang.BTN_REQUEST_FAILS, r.code() + " - " + body));
                            setLastStatus(false, "HTTP Error: " + r.code() + " - " + body);
                        } catch (IOException ignored) {
                        }
                    } else {
                        try {
                            String btrString = r.body().string();
                            BtnRule btr = JsonUtil.getGson().fromJson(btrString, BtnRule.class);
                            this.btnRule = new BtnRuleParsed(btr);
                            Main.getEventBus().post(new BtnRuleUpdateEvent());
                            try {
                                Files.writeString(btnCacheFile.toPath(), btrString, StandardCharsets.UTF_8);
                            } catch (IOException ignored) {
                            }
                            log.info(tlUI(Lang.BTN_UPDATE_RULES_SUCCESSES, this.btnRule.getVersion()));
                            setLastStatus(true, "Loaded from remote, version: " + this.btnRule.getVersion());
                            btnNetwork.getModuleMatchCache().invalidateAll();
                        } catch (JsonSyntaxException | IOException e) {
                            try {
                                String body = r.body().string();
                                setLastStatus(false, "Unable parse remote JSON response: " + r.code() + " - " + body);
                                log.error("Unable to parse BtnRule as a valid Json object: {}-{}", r.code(), body, e);
                            } catch (IOException ignored) {
                            }
                        }
                    }
                })
                .exceptionally((e) -> {
                    log.error(tlUI(Lang.BTN_REQUEST_FAILS), e);
                    setLastStatus(false, "Unknown Error: " + e.getClass().getName() + ": " + e.getMessage());
                    return null;
                });
    }

    @Override
    public void unload() {

    }
}
