package com.ghostchu.peerbanhelper.btn;

import com.ghostchu.peerbanhelper.DownloaderServer;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.btn.ability.BtnAbility;
import com.ghostchu.peerbanhelper.btn.ability.impl.*;
import com.ghostchu.peerbanhelper.btn.ability.impl.legacy.*;
import com.ghostchu.peerbanhelper.database.dao.impl.HistoryDao;
import com.ghostchu.peerbanhelper.database.dao.impl.MetadataDao;
import com.ghostchu.peerbanhelper.database.dao.impl.PeerRecordDao;
import com.ghostchu.peerbanhelper.database.dao.impl.tmp.TrackedSwarmDao;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.rule.ModuleMatchCache;
import com.ghostchu.peerbanhelper.util.scriptengine.ScriptEngine;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Component;

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
@Component
// 特别注意：该类不允许静态初始化任何内容
public final class BtnNetwork implements Reloadable {
    @Getter
    private final Map<Class<? extends BtnAbility>, BtnAbility> abilities = Collections.synchronizedMap(new HashMap<>());

    private final ScriptEngine scriptEngine;
    @Getter
    private final AtomicBoolean configSuccess = new AtomicBoolean(false);
    @Getter
    private TranslationComponent configResult;
    private boolean scriptExecute;
    private ScheduledExecutorService scheduler = null;
    @Getter
    private String configUrl;
    @Getter
    private boolean submit;
    @Getter
    private String appId;
    @Getter
    private String appSecret;
    private OkHttpClient httpClient;
    private final DownloaderServer server;
    @Getter
    private final PeerRecordDao peerRecordDao;
    @Getter
    private final TrackedSwarmDao trackedSwarmDao;
    @Getter
    private final MetadataDao metadataDao;
    @Getter
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
        new Thread(() -> {
            Main.getReloadManager().register(this);
            reloadConfig();
        }).start();
    }

    @Override
    public ReloadResult reloadModule() throws Exception {
        new Thread(this::reloadConfig).start();
        return Reloadable.super.reloadModule();
    }

    public synchronized void reloadConfig() {
        log.info("Reconfiguring BtnNetwork...");
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
        if (scheduler != null) {
            scheduler.shutdown();
        }
        if (enabled) {
            scheduler = Executors.newScheduledThreadPool(2);
            scheduler.scheduleWithFixedDelay(this::checkIfNeedRetryConfig, 0, 600, TimeUnit.SECONDS);
        } else {
            scheduler = null;
        }
    }

    public synchronized void configBtnNetwork() {
        String response;
        int statusCode;
        Request request = new Request.Builder()
                .url(configUrl)
                .get()
                .build();
        try (Response resp = httpClient.newCall(request).execute()) {
            statusCode = resp.code();
            if (!resp.isSuccessful()) {
                response = resp.body().string();
                log.error(tlUI(Lang.BTN_CONFIG_FAILS, statusCode + " - " + response, 600));
                configResult = new TranslationComponent(Lang.BTN_CONFIG_STATUS_UNSUCCESSFUL_HTTP_REQUEST, configUrl, statusCode, response);
                return;
            }
            response = resp.body().string();
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
                    abilities.put(LegacyBtnAbilitySubmitPeers.class, new LegacyBtnAbilitySubmitPeers(this, ability.get("submit_peers").getAsJsonObject()));
                }
                if (ability.has("submit_bans") && submit) {
                    abilities.put(LegacyBtnAbilitySubmitBans.class, new LegacyBtnAbilitySubmitBans(this, ability.get("submit_bans").getAsJsonObject()));
                }
                if (ability.has("submit_histories") && submit) {
                    abilities.put(LegacyBtnAbilitySubmitHistory.class, new LegacyBtnAbilitySubmitHistory(this, ability.get("submit_histories").getAsJsonObject()));
                }
                if (ability.has("rules")) {
                    abilities.put(LegacyBtnAbilityRules.class, new LegacyBtnAbilityRules(this, scriptEngine, ability.get("rules").getAsJsonObject(), scriptExecute));
                }
                if (ability.has("exception")) {
                    abilities.put(LegacyBtnAbilityException.class, new BtnAbilityReconfigure(this, ability.get("exception").getAsJsonObject()));
                }
            } else {
                if (ability.has("submit_bans") && submit) {
                    abilities.put(BtnAbilitySubmitBans.class, new BtnAbilitySubmitBans(this, ability.get("submit_bans").getAsJsonObject(), metadataDao, historyDao));
                }
                if (ability.has("submit_swarm") && submit) {
                    abilities.put(BtnAbilitySubmitSwarm.class, new BtnAbilitySubmitSwarm(this, ability.get("submit_swarm").getAsJsonObject(), metadataDao, trackedSwarmDao));
                }
                if (ability.has("ip_denylist")) {
                    abilities.put(BtnAbilityIPDenyList.class, new BtnAbilityIPDenyList(this, metadataDao, ability.get("ip_denylist").getAsJsonObject()));
                }
                if (ability.has("ip_allowlist")) {
                    abilities.put(BtnAbilityIPAllowList.class, new BtnAbilityIPAllowList(this, metadataDao, ability.get("ip_denylist").getAsJsonObject()));
                }
            }
            if (ability.has("reconfigure")) {
                abilities.put(BtnAbilityReconfigure.class, new BtnAbilityReconfigure(this, ability.get("reconfigure").getAsJsonObject()));
            }
            if (ability.has("heartbeat")) {
                abilities.put(BtnAbilityHeartBeat.class, new BtnAbilityHeartBeat(this, ability.get("heartbeat").getAsJsonObject()));
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
            log.error(tlUI(Lang.BTN_CONFIG_FAILS, e.getMessage()), e);
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
        this.httpClient = httpUtil.newBuilder()
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    Request.Builder requestBuilder = original.newBuilder()
                            .header("User-Agent", Main.getUserAgent())
                            .header("Content-Type", "application/json")
                            .header("BTN-AppID", appId)
                            .header("BTN-AppSecret", appSecret)
                            .header("X-BTN-AppID", appId)
                            .header("X-BTN-AppSecret", appSecret)
                            .header("Authentication", "Bearer " + appId + "@" + appSecret);
                    return chain.proceed(requestBuilder.build());
                })
                .authenticator((route, response) -> response.request().newBuilder().header("Authorization", "Bearer " + appId + "@" + appSecret).build())
                .callTimeout(Duration.ofMinutes(1))
                .build();
    }

    public OkHttpClient getHttpClient() {
        return httpClient;
    }

    public ModuleMatchCache getModuleMatchCache() {
        return moduleMatchCache;
    }

    public DownloaderServer getServer() {
        return server;
    }

    public ScheduledExecutorService getScheduler() {
        return scheduler;
    }

    public HTTPUtil getHttpUtil() {
        return httpUtil;
    }

    public void close() {
        log.info(tlUI(Lang.BTN_SHUTTING_DOWN));
        scheduler.shutdown();
        abilities.values().forEach(BtnAbility::unload);
        abilities.clear();
    }
}
