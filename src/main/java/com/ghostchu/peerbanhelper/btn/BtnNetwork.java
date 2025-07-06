package com.ghostchu.peerbanhelper.btn;

import com.ghostchu.peerbanhelper.DownloaderServer;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.btn.ability.BtnAbility;
import com.ghostchu.peerbanhelper.btn.ability.impl.*;
import com.ghostchu.peerbanhelper.btn.ability.impl.legacy.LegacyBtnAbilitySubmitBans;
import com.ghostchu.peerbanhelper.btn.ability.impl.legacy.LegacyBtnAbilitySubmitHistory;
import com.ghostchu.peerbanhelper.btn.ability.impl.legacy.LegacyBtnAbilitySubmitPeers;
import com.ghostchu.peerbanhelper.database.dao.impl.HistoryDao;
import com.ghostchu.peerbanhelper.database.dao.impl.MetadataDao;
import com.ghostchu.peerbanhelper.database.dao.impl.PeerRecordDao;
import com.ghostchu.peerbanhelper.database.dao.impl.tmp.TrackedSwarmDao;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.CommonUtil;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.rule.ModuleMatchCache;
import com.ghostchu.peerbanhelper.util.scriptengine.ScriptEngine;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import com.github.mizosoft.methanol.Methanol;
import com.github.mizosoft.methanol.MutableRequest;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
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
    private final DownloaderServer server;
    private final PeerRecordDao peerRecordDao;
    private final TrackedSwarmDao trackedSwarmDao;
    private final MetadataDao metadataDao;
    private final HistoryDao historyDao;
    private final HTTPUtil httpUtil;
    private final ModuleMatchCache moduleMatchCache;
    private boolean enabled;

    public BtnNetwork(ScriptEngine scriptEngine, ModuleMatchCache moduleMatchCache, DownloaderServer downloaderServer, HTTPUtil httpUtil,
                      MetadataDao metadataDao, HistoryDao historyDao, TrackedSwarmDao trackedSwarmDao, PeerRecordDao peerRecordDao) {
        this.server = downloaderServer;
        this.scriptEngine = scriptEngine;
        this.moduleMatchCache = moduleMatchCache;
        this.httpUtil = httpUtil;
        this.metadataDao = metadataDao;
        this.historyDao = historyDao;
        this.peerRecordDao = peerRecordDao;
        this.trackedSwarmDao = trackedSwarmDao;
        Main.getReloadManager().register(this);
        reloadConfig();
    }

    @Override
    public ReloadResult reloadModule() throws Exception {
        Thread.startVirtualThread(this::reloadConfig);
        return Reloadable.super.reloadModule();
    }

    public synchronized void reloadConfig() {
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
        log.info("BtnNetwork reloaded");
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
            HttpResponse<String> resp = httpUtil.retryableSend(httpClient, MutableRequest.GET(configUrl), HttpResponse.BodyHandlers.ofString()).join();
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
            boolean useLegacyAbilities = min_protocol_version <= 10;
            resetScheduler();
            abilities.values().forEach(BtnAbility::unload);
            abilities.clear();
            JsonObject ability = json.get("ability").getAsJsonObject();
            if (useLegacyAbilities) {
                if (ability.has("submit_peers") && submit) {
                    abilities.put(LegacyBtnAbilitySubmitPeers.class, new LegacyBtnAbilitySubmitPeers(this, httpUtil, ability.get("submit_peers").getAsJsonObject()));
                }
                if (ability.has("submit_bans") && submit) {
                    abilities.put(LegacyBtnAbilitySubmitBans.class, new LegacyBtnAbilitySubmitBans(this, httpUtil, ability.get("submit_bans").getAsJsonObject()));
                }
                if (ability.has("submit_histories") && submit) {
                    abilities.put(LegacyBtnAbilitySubmitHistory.class, new LegacyBtnAbilitySubmitHistory(this, httpUtil, ability.get("submit_histories").getAsJsonObject()));
                }
            } else {
                if (ability.has("submit_bans") && submit) {
                    abilities.put(BtnAbilitySubmitBans.class, new BtnAbilitySubmitBans(this, ability.get("submit_bans").getAsJsonObject(), metadataDao, historyDao));
                }
                if (ability.has("submit_swarm") && submit) {
                    abilities.put(BtnAbilitySubmitSwarm.class, new BtnAbilitySubmitSwarm(this, ability.get("submit_swarm").getAsJsonObject(), metadataDao, trackedSwarmDao));
                }
            }
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
            log.error(tlUI(Lang.BTN_CONFIG_FAILS, statusCode + " - " + response, 600), e);
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
                .executor(Executors.newVirtualThreadPerTaskExecutor())
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
                .headersTimeout(Duration.ofSeconds(15))
                .readTimeout(Duration.ofSeconds(30), CommonUtil.getScheduler())
                .cookieHandler(cm).build();
    }

    public void close() {
        log.info(tlUI(Lang.BTN_SHUTTING_DOWN));
        executeService.shutdown();
        abilities.values().forEach(BtnAbility::unload);
        abilities.clear();
    }
}
