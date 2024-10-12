package com.ghostchu.peerbanhelper.btn;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.btn.ability.*;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
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
    private static final int PBH_BTN_PROTOCOL_IMPL_VERSION = 7;
    @Getter
    private final Map<Class<? extends BtnAbility>, BtnAbility> abilities = new HashMap<>();
    @Getter
    private ScheduledExecutorService executeService = null;
    private String configUrl;
    private boolean submit;
    private String appId;
    private String appSecret;
    @Getter
    private OkHttpClient httpClient;
    @Autowired
    @Qualifier("userAgent")
    private String userAgent;
    private PeerBanHelperServer server;
    private final AtomicBoolean configSuccess = new AtomicBoolean(false);

    public BtnNetwork(PeerBanHelperServer server, String userAgent, String configUrl, boolean submit, String appId, String appSecret) {
        this.server = server;
        this.userAgent = userAgent;
        this.configUrl = configUrl;
        this.submit = submit;
        this.appId = appId;
        this.appSecret = appSecret;
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
        try (Response resp = HTTPUtil.retryableSend(httpClient, new Request.Builder().url(configUrl).build()).join()) {
            if (resp.code() != 200) {
                log.error(tlUI(Lang.BTN_CONFIG_FAILS, resp.code() + " - " + resp.body().string(), 600));
                return;
            }
            JsonObject json = JsonParser.parseString(resp.body().string()).getAsJsonObject();
            if (!json.has("min_protocol_version")) {
                throw new IllegalStateException("Server config response missing min_protocol_version field");
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
            if (ability.has("submit_hitrate") && submit) {
                abilities.put(BtnAbilitySubmitRulesHitRate.class, new BtnAbilitySubmitRulesHitRate(this, ability.get("submit_hitrate").getAsJsonObject()));
            }
            if (ability.has("rules")) {
                abilities.put(BtnAbilityRules.class, new BtnAbilityRules(this, ability.get("rules").getAsJsonObject()));
            }
            if (ability.has("reconfigure")) {
                abilities.put(BtnAbilityReconfigure.class, new BtnAbilityReconfigure(this, ability.get("reconfigure").getAsJsonObject()));
            }
            abilities.values().forEach(a -> {
                try {
                    a.load();
                } catch (Exception e) {
                    log.error("Failed to load BTN ability", e);
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
            log.error("Unable to complete scheduled tasks", throwable);
        }

    }

    private void setupHttpClient() {
        CookieManager cm = new CookieManager();
        cm.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        this.httpClient = Main.getSharedHttpClient().newBuilder()
                .followRedirects(true)
                .followSslRedirects(true)
                .readTimeout(Duration.ofMinutes(1))
                .connectTimeout(Duration.ofSeconds(10))
                .cookieJar(new JavaNetCookieJar(cm))
                .addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(Interceptor.Chain chain) throws IOException {
                        Request original = chain.request();
                        Request request = original.newBuilder()
                                .header("User-Agent", userAgent)
                                .header("Accept-Encoding", "gzip,deflate")
                                .header("Content-Type", "application/json")
                                .header("BTN-AppID", appId)
                                .header("BTN-AppSecret", appSecret)
                                .header("X-BTN-AppID", appId)
                                .header("X-BTN-AppSecret", appSecret)
                                .header("Authentication", "Bearer " + appId + "@" + appSecret)
                                .method(original.method(), original.body())
                                .build();
                        return chain.proceed(request);
                    }
                })
                .build();
    }

    public void close() {
        log.info(tlUI(Lang.BTN_SHUTTING_DOWN));
        executeService.shutdown();
        abilities.values().forEach(BtnAbility::unload);
        abilities.clear();
    }
}
