package com.ghostchu.peerbanhelper.btn;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.btn.ability.*;
import com.ghostchu.peerbanhelper.database.dao.impl.PeerRecordDao;
import com.ghostchu.peerbanhelper.scriptengine.ScriptEngine;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.rule.ModuleMatchCache;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import com.github.mizosoft.methanol.Methanol;
import com.github.mizosoft.methanol.MutableRequest;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
@Getter
@Component
// 特别注意：该类不允许静态初始化任何内容
public final class BtnNetwork implements Reloadable {
    @Getter
    private final Map<Class<? extends BtnAbility>, BtnAbility> abilities = Collections.synchronizedMap(new HashMap<>());

    private final ScriptEngine scriptEngine;
    private final AtomicBoolean configSuccess = new AtomicBoolean(false);
    private TranslationComponent configResult;
    private boolean scriptExecute;
    @Getter
    private ScheduledExecutorService executeService = null;
    private String configUrl;
    private boolean submit;
    private String appId;
    private String appSecret;
    @Getter
    private HttpClient httpClient;
    private PeerBanHelperServer server;
    @Autowired
    private PeerRecordDao peerRecordDao;
    private ModuleMatchCache moduleMatchCache;
    private boolean enabled;

    public BtnNetwork(PeerBanHelperServer server, ScriptEngine scriptEngine, ModuleMatchCache moduleMatchCache) {
        this.server = server;
        this.scriptEngine = scriptEngine;
        this.moduleMatchCache = moduleMatchCache;
        Main.getReloadManager().register(this);
        reloadConfig();
    }

    @Override
    public ReloadResult reloadModule() throws Exception {
        reloadConfig();
        log.info("BtnNetwork reloaded");
        return Reloadable.super.reloadModule();
    }

    public void reloadConfig() {
        this.enabled = Main.getMainConfig().getBoolean("btn.enabled");
        this.configUrl = Main.getMainConfig().getString("btn.config-url");
        this.submit = Main.getMainConfig().getBoolean("btn.submit");
        this.appId = Main.getMainConfig().getString("btn.app-id");
        this.appSecret = Main.getMainConfig().getString("btn.app-secret");
        this.scriptExecute = Main.getMainConfig().getBoolean("btn.allow-script-execute");
        configSuccess.set(false);
        configResult = null;
        resetAbilities();
        setupHttpClient();
        resetScheduler();
        checkIfNeedRetryConfig();
    }

    private void resetAbilities() {
        abilities.values().forEach(BtnAbility::unload);
        abilities.clear();
    }

    private void resetScheduler() {
        if (executeService != null) {
            executeService.shutdownNow();
        }
        if (enabled) {
            executeService = Executors.newScheduledThreadPool(2);
            executeService.scheduleWithFixedDelay(this::checkIfNeedRetryConfig, 0, 600, TimeUnit.SECONDS);
        } else {
            executeService = null;
        }
    }

    public synchronized void configBtnNetwork() {
        String response = "<Not Provided>";
        int statusCode = 0;
        try {
            HttpResponse<String> resp = HTTPUtil.retryableSend(httpClient, MutableRequest.GET(configUrl), HttpResponse.BodyHandlers.ofString()).join();
            if (resp.statusCode() != 200) {
                log.error(tlUI(Lang.BTN_CONFIG_FAILS, resp.statusCode() + " - " + resp.body(), 600));
                configResult = new TranslationComponent(Lang.BTN_CONFIG_STATUS_UNSUCCESSFUL_HTTP_REQUEST, configUrl, resp.statusCode(), resp.body());
                return;
            }
            statusCode = resp.statusCode();
            response = resp.body();
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            if (!json.has("min_protocol_version")) {
                throw new IllegalStateException(tlUI(Lang.MISSING_VERSION_PROTOCOL_FIELD));
            }
            int min_protocol_version = json.get("min_protocol_version").getAsInt();
            if (Main.PBH_BTN_PROTOCOL_IMPL_VERSION < min_protocol_version) {
                configResult = new TranslationComponent(Lang.BTN_CONFIG_STATUS_UNSUCCESSFUL_INCOMPATIBLE_BTN_PROTOCOL_VERSION_CLIENT, Main.PBH_BTN_PROTOCOL_IMPL_VERSION, min_protocol_version);
                throw new IllegalStateException(tlUI(configResult));
            }
            int max_protocol_version = json.get("max_protocol_version").getAsInt();
            if (Main.PBH_BTN_PROTOCOL_IMPL_VERSION > max_protocol_version) {
                configResult = new TranslationComponent(Lang.BTN_CONFIG_STATUS_UNSUCCESSFUL_INCOMPATIBLE_BTN_PROTOCOL_VERSION_SERVER, Main.PBH_BTN_PROTOCOL_IMPL_VERSION, max_protocol_version);
                throw new IllegalStateException(tlUI(Lang.BTN_INCOMPATIBLE_SERVER));
            }
            resetScheduler();
            abilities.values().forEach(BtnAbility::unload);
            abilities.clear();
            JsonObject ability = json.get("ability").getAsJsonObject();
            if (ability.has("submit_peers") && submit) {
                abilities.put(BtnAbilitySubmitPeers.class, new BtnAbilitySubmitPeers(this, ability.get("submit_peers").getAsJsonObject()));
            }
            if (ability.has("submit_bans") && submit) {
                abilities.put(BtnAbilitySubmitBans.class, new BtnAbilitySubmitBans(this, ability.get("submit_bans").getAsJsonObject()));
            }
            if (ability.has("submit_histories") && submit) {
                abilities.put(BtnAbilitySubmitHistory.class, new BtnAbilitySubmitHistory(this, ability.get("submit_histories").getAsJsonObject()));
            }
//            if (ability.has("submit_hitrate") && submit) {
//                abilities.put(BtnAbilitySubmitRulesHitRate.class, new BtnAbilitySubmitRulesHitRate(this, ability.get("submit_hitrate").getAsJsonObject()));
//            }
            if (ability.has("rules")) {
                abilities.put(BtnAbilityRules.class, new BtnAbilityRules(this, scriptEngine, ability.get("rules").getAsJsonObject(), scriptExecute));
            }
            if (ability.has("reconfigure")) {
                abilities.put(BtnAbilityReconfigure.class, new BtnAbilityReconfigure(this, ability.get("reconfigure").getAsJsonObject()));
            }
            if (ability.has("exception")) {
                abilities.put(BtnAbilityException.class, new BtnAbilityReconfigure(this, ability.get("exception").getAsJsonObject()));
            }
            abilities.values().forEach(a -> {
                try {
                    a.load();
                } catch (Exception e) {
                    log.error(tlUI(Lang.UNABLE_LOAD_BTN_ABILITY, a.getClass().getSimpleName()), e);
                }
            });
            configSuccess.set(true);
            configResult = new TranslationComponent(Lang.BTN_CONFIG_STATUS_SUCCESSFUL);
        } catch (Throwable e) {
            log.error(tlUI(Lang.BTN_CONFIG_FAILS, statusCode+" - "+response, 600), e);
            configResult = new TranslationComponent(Lang.BTN_CONFIG_STATUS_EXCEPTION, e.getClass().getName(), e.getMessage());
            configSuccess.set(false);
        }
    }

    private void checkIfNeedRetryConfig() {
        try {
            if (enabled) {
                if (!configSuccess.get()) {
                    configBtnNetwork();
                }
            } else {
                configSuccess.set(false);
            }
        } catch (Throwable throwable) {
            log.error(tlUI(Lang.UNABLE_COMPLETE_SCHEDULE_TASKS), throwable);
        }

    }

    private void setupHttpClient() {
        CookieManager cm = new CookieManager();
        cm.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        this.httpClient = Methanol
                .newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .userAgent(Main.getUserAgent())
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("BTN-AppID", appId)
                .defaultHeader("BTN-AppSecret", appSecret)
                .defaultHeader("X-BTN-AppID", appId)
                .defaultHeader("X-BTN-AppSecret", appSecret)
                .defaultHeader("Authentication", "Bearer " + appId + "@" + appSecret)
                .requestTimeout(Duration.ofMinutes(1))
                .connectTimeout(Duration.ofSeconds(10))
                .cookieHandler(cm).build();
    }

    public void close() {
        log.info(tlUI(Lang.BTN_SHUTTING_DOWN));
        executeService.shutdown();
        abilities.values().forEach(BtnAbility::unload);
        abilities.clear();
    }
}
