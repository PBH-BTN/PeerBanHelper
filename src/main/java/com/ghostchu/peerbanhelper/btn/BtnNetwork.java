package com.ghostchu.peerbanhelper.btn;

import com.ghostchu.peerbanhelper.DownloaderServer;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.btn.ability.BtnAbility;
import com.ghostchu.peerbanhelper.btn.ability.impl.*;
import com.ghostchu.peerbanhelper.btn.ability.impl.legacy.LegacyBtnAbilitySubmitBans;
import com.ghostchu.peerbanhelper.btn.ability.impl.legacy.LegacyBtnAbilitySubmitPeers;
import com.ghostchu.peerbanhelper.databasent.service.HistoryService;
import com.ghostchu.peerbanhelper.databasent.service.MetadataService;
import com.ghostchu.peerbanhelper.databasent.service.TorrentService;
import com.ghostchu.peerbanhelper.databasent.service.TrackedSwarmService;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.util.pow.PoWClient;
import com.ghostchu.peerbanhelper.util.rule.ModuleMatchCache;
import com.ghostchu.peerbanhelper.util.scriptengine.ScriptEngineManager;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import com.google.common.hash.Hashing;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.sentry.Sentry;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import oshi.SystemInfo;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
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

    private final ScriptEngineManager scriptEngineManager;
    @Getter
    private final AtomicBoolean configSuccess = new AtomicBoolean(false);
    private final SystemInfo systemInfo;
    private final TorrentService torrentDao;
    @Getter
    private TranslationComponent configResult;
    private boolean scriptExecute;
    @Getter
    private ScheduledExecutorService scheduler = null;
    @Getter
    private String configUrl;
    @Getter
    private boolean submit;
    @Getter
    private String appId;
    @Getter
    private String appSecret;
    @Getter
    private OkHttpClient httpClient;
    @Getter
    private final DownloaderServer server;
    @Getter
    private final TrackedSwarmService trackedSwarmDao;
    @Getter
    private final MetadataService metadataDao;
    @Getter
    private final HistoryService historyDao;
    @Getter
    private final HTTPUtil httpUtil;
    @Getter
    private final ModuleMatchCache moduleMatchCache;
    private boolean enabled;
    private String powCaptchaEndpoint;
    private long nextConfigAttemptTime = 0;

    public BtnNetwork(ScriptEngineManager scriptEngineManager, ModuleMatchCache moduleMatchCache, DownloaderServer downloaderServer, HTTPUtil httpUtil,
                      MetadataService metadataDao, HistoryService historyDao, TrackedSwarmService trackedSwarmDao, SystemInfo systemInfo, TorrentService torrentService) {
        this.server = downloaderServer;
        this.scriptEngineManager = scriptEngineManager;
        this.moduleMatchCache = moduleMatchCache;
        this.httpUtil = httpUtil;
        this.metadataDao = metadataDao;
        this.historyDao = historyDao;
        this.trackedSwarmDao = trackedSwarmDao;
        this.torrentDao = torrentService;
        var thr = new Thread(() -> {
            Main.getReloadManager().register(this);
            reloadConfig();
        });
        thr.start();
        this.systemInfo = systemInfo;
    }

    @Override
    public ReloadResult reloadModule() throws Exception {
        var thr = new Thread(this::reloadConfig);
        thr.start();
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
        nextConfigAttemptTime = System.currentTimeMillis();
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
            response = resp.body().string();
            if (!resp.isSuccessful()) {
                log.error(tlUI(Lang.BTN_CONFIG_FAILS, statusCode + " - " + response, 600));
                configResult = new TranslationComponent(Lang.BTN_CONFIG_STATUS_UNSUCCESSFUL_HTTP_REQUEST, configUrl, statusCode, response);
                return;
            }
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
            boolean useLegacyAbilities = min_protocol_version < 20;
            resetScheduler();
            abilities.values().forEach(BtnAbility::unload);
            abilities.clear();
            if (json.has("proof_of_work_captcha") && !json.isJsonNull()) {
                JsonObject powCaptcha = json.get("proof_of_work_captcha").getAsJsonObject();
                this.powCaptchaEndpoint = powCaptcha.get("endpoint").getAsString();
            }
            JsonObject ability = json.get("ability").getAsJsonObject();
            if (useLegacyAbilities) {
                if (ability.has("submit_peers") && submit) {
                    abilities.put(LegacyBtnAbilitySubmitPeers.class, new LegacyBtnAbilitySubmitPeers(this, ability.get("submit_peers").getAsJsonObject()));
                }
                if (ability.has("submit_bans") && submit) {
                    abilities.put(LegacyBtnAbilitySubmitBans.class, new LegacyBtnAbilitySubmitBans(this, ability.get("submit_bans").getAsJsonObject()));
                }
                if (ability.has("rules")) {
                    abilities.put(BtnAbilityRules.class, new BtnAbilityRules(this, metadataDao, scriptEngineManager, ability.get("rules").getAsJsonObject(), scriptExecute));
                }
            } else {
                if (ability.has("submit_bans") && submit) {
                    abilities.put(BtnAbilitySubmitBans.class, new BtnAbilitySubmitBans(this, ability.get("submit_bans").getAsJsonObject(), metadataDao, historyDao, torrentDao));
                }
                if (ability.has("submit_swarm") && submit) {
                    abilities.put(BtnAbilitySubmitSwarm.class, new BtnAbilitySubmitSwarm(this, ability.get("submit_swarm").getAsJsonObject(), metadataDao, trackedSwarmDao));
                }
                if (ability.has("ip_denylist")) {
                    abilities.put(BtnAbilityIPDenyList.class, new BtnAbilityIPDenyList(this, metadataDao, ability.get("ip_denylist").getAsJsonObject()));
                }
                if (ability.has("ip_allowlist")) {
                    abilities.put(BtnAbilityIPAllowList.class, new BtnAbilityIPAllowList(this, metadataDao, ability.get("ip_allowlist").getAsJsonObject()));
                }
                if (ability.has("rule_peer_identity")) {
                    abilities.put(BtnAbilityRules.class, new BtnAbilityRules(this, metadataDao, scriptEngineManager, ability.get("rule_peer_identity").getAsJsonObject(), scriptExecute));
                }
            }
            if (ability.has("submit_histories") && submit) {
                abilities.put(BtnAbilitySubmitHistory.class, new BtnAbilitySubmitHistory(this, metadataDao, ability.get("submit_histories").getAsJsonObject(), torrentDao));
            }
            if (ability.has("reconfigure")) {
                abilities.put(BtnAbilityReconfigure.class, new BtnAbilityReconfigure(this, ability.get("reconfigure").getAsJsonObject()));
            }
            if (ability.has("heartbeat")) {
                abilities.put(BtnAbilityHeartBeat.class, new BtnAbilityHeartBeat(this, ability.get("heartbeat").getAsJsonObject()));
            }
            if (ability.has("ip_query")) {
                abilities.put(BtnAbilityIpQuery.class, new BtnAbilityIpQuery(this, ability.get("ip_query").getAsJsonObject()));
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
            nextConfigAttemptTime = System.currentTimeMillis() + 600 * 1000;
            Sentry.captureException(e);
        }
    }

    public void gatherAndSolveCaptchaBlocking(@NotNull Request.Builder requestBuilder, @NotNull String type) {
        if (powCaptchaEndpoint == null) return;
        Request request = new Request.Builder()
                .url(powCaptchaEndpoint + "?type=" + type)
                .get()
                .build();
        try (Response resp = httpClient.newCall(request).execute()) {
            String respContent = resp.body().string();
            if (!resp.isSuccessful()) {
                log.error(tlUI(Lang.BTN_POW_CAPTCHA_LOAD_FROM_REMOTE, resp.code(), respContent));
                return;
            }
            PowCaptchaData powCaptchaData = JsonUtil.standard().fromJson(respContent, PowCaptchaData.class);
            long startTime = System.currentTimeMillis();
            try (PoWClient poWClient = new PoWClient()) {
                log.debug(tlUI(Lang.BTN_POW_CAPTCHA_COMPUTING));
                byte[] nonce = poWClient.solve(
                        Base64.getDecoder().decode(powCaptchaData.getChallengeBase64()),
                        powCaptchaData.getDifficultyBits(),
                        powCaptchaData.getAlgorithm()
                );
                requestBuilder.header("X-BTN-PowID", powCaptchaData.getId())
                        .header("X-BTN-PowSolution", Base64.getEncoder().encodeToString(nonce));
                long costTime = System.currentTimeMillis() - startTime;
                log.debug(tlUI(Lang.BTN_POW_CAPTCHA_COMPUTE_COMPLETED, costTime));
            }
        } catch (Throwable e) {
            log.error("Unable to gather or solve PoW Captcha", e);
            Sentry.captureException(e);
        }
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class PowCaptchaData {
        private String id;
        private String challengeBase64;
        private int difficultyBits;
        private String algorithm;
        private long expireAt;
    }

    private void checkIfNeedRetryConfig() {
        try {
            if (enabled) {
                if (!configSuccess.get() && System.currentTimeMillis() > nextConfigAttemptTime) {
                    configBtnNetwork();
                }
            } else {
                configSuccess.set(false);
            }
        } catch (Throwable throwable) {
            log.error(tlUI(Lang.UNABLE_COMPLETE_SCHEDULE_TASKS), throwable);
            Sentry.captureException(throwable);
        }

    }

    private void setupHttpClient() {
        this.httpClient = httpUtil.newBuilder()
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    Request.Builder requestBuilder = original.newBuilder()
                            .header("User-Agent", Main.getUserAgent())
                            .header("Content-Type", "application/json")
                            .header("BTN-AppID", appId) // Legacy Protocol
                            .header("BTN-AppSecret", appSecret) // Legacy Protocol
                            .header("X-BTN-AppID", appId)
                            .header("X-BTN-AppSecret", appSecret)
                            .header("Authentication", "Bearer " + appId + "@" + appSecret); // For anonymous account
                    if ((appId == null || appId.isBlank() || appId.equals("example-app-id"))
                            || (appSecret == null || appSecret.isBlank() || appSecret.equals("example-app-secret"))) {
                        requestBuilder.header("X-BTN-InstallationID", getInstallationId());

                    }
                    return chain.proceed(requestBuilder.build());
                })
                .authenticator((route, response) -> response.request().newBuilder().header("Authorization", "Bearer " + appId + "@" + appSecret).build())
                .callTimeout(Duration.ofMinutes(1))
                .build();
    }

    @NotNull
    public String getInstallationId() {
        return Main.getMainConfig().getString("installation-id", "");
    }

    @NotNull
    public String getBtnHardwareId() {
        return Hashing.sha256().hashString(systemInfo.getHardware().getComputerSystem().getHardwareUUID(), StandardCharsets.UTF_8).toString();
    }

    public void close() {
        log.info(tlUI(Lang.BTN_SHUTTING_DOWN));
        scheduler.shutdown();
        abilities.values().forEach(BtnAbility::unload);
        abilities.clear();
    }
}
