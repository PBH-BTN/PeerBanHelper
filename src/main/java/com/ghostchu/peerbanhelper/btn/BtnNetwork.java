package com.ghostchu.peerbanhelper.btn;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.btn.ability.*;
import com.ghostchu.peerbanhelper.database.dao.impl.PeerRecordDao;
import com.ghostchu.peerbanhelper.scriptengine.ScriptEngine;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.rule.ModuleMatchCache;
import com.github.mizosoft.methanol.Methanol;
import com.github.mizosoft.methanol.MutableRequest;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
@Getter
public class BtnNetwork {
    private static final int PBH_BTN_PROTOCOL_IMPL_VERSION = 8;
    @Getter
    private final Map<Class<? extends BtnAbility>, BtnAbility> abilities = new HashMap<>();
    private final ScriptEngine scriptEngine;
    private final boolean scriptExecute;
    @Getter
    private ScheduledExecutorService executeService = null;
    private String configUrl;
    private boolean submit;
    private String appId;
    private String appSecret;
    @Getter
    private HttpClient httpClient;
    @Autowired
    @Qualifier("userAgent")
    private String userAgent;
    private PeerBanHelperServer server;
    private final AtomicBoolean configSuccess = new AtomicBoolean(false);
    @Autowired
    private PeerRecordDao peerRecordDao;
    private ModuleMatchCache moduleMatchCache;

    public BtnNetwork(PeerBanHelperServer server, ScriptEngine scriptEngine, String userAgent, String configUrl, boolean submit, String appId, String appSecret, ModuleMatchCache moduleMatchCache, boolean scriptExecute) {
        this.server = server;
        this.scriptEngine = scriptEngine;
        this.userAgent = userAgent;
        this.configUrl = configUrl;
        this.submit = submit;
        this.appId = appId.trim();
        this.appSecret = appSecret.trim();
        this.moduleMatchCache = moduleMatchCache;
        this.scriptExecute = scriptExecute;
        setupHttpClient();
        resetScheduler();
        checkIfNeedRetryConfig();
    }

    private void resetScheduler() {
        if (executeService != null) {
            executeService.shutdownNow();
        }
        executeService = Executors.newScheduledThreadPool(2);
        executeService.scheduleWithFixedDelay(this::checkIfNeedRetryConfig, 600, 600, TimeUnit.SECONDS);
    }

    public void configBtnNetwork() {
        try {
            HttpResponse<String> resp = HTTPUtil.retryableSend(httpClient, MutableRequest.GET(configUrl), HttpResponse.BodyHandlers.ofString()).join();
            if (resp.statusCode() != 200) {
                log.error(tlUI(Lang.BTN_CONFIG_FAILS, resp.statusCode() + " - " + resp.body(), 600));
                return;
            }
            JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
            if (!json.has("min_protocol_version")) {
                throw new IllegalStateException(tlUI(Lang.MISSING_VERSION_PROTOCOL_FIELD));
            }
            int min_protocol_version = json.get("min_protocol_version").getAsInt();
            if (PBH_BTN_PROTOCOL_IMPL_VERSION < min_protocol_version) {
                throw new IllegalStateException(tlUI(Lang.BTN_INCOMPATIBLE_SERVER));
            }
            int max_protocol_version = json.get("max_protocol_version").getAsInt();
            if (PBH_BTN_PROTOCOL_IMPL_VERSION > max_protocol_version) {
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
        } catch (Throwable e) {
            log.error(tlUI(Lang.BTN_CONFIG_FAILS, 600), e);
        }
    }

    private void checkIfNeedRetryConfig() {
        try {
            if (!configSuccess.get()) {
                configBtnNetwork();
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
                .userAgent(userAgent)
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
