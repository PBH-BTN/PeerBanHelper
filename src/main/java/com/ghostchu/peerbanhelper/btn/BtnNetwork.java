package com.ghostchu.peerbanhelper.btn;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.btn.ability.*;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
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

@Slf4j
@Getter
public class BtnNetwork {
    private static final int BTN_PROTOCOL_VERSION = 5;
    @Getter
    private final Map<Class<? extends BtnAbility>, BtnAbility> abilities = new HashMap<>();
    @Getter
    private final ScheduledExecutorService executeService = Executors.newScheduledThreadPool(2);
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

    public BtnNetwork(PeerBanHelperServer server, String userAgent, String configUrl, boolean submit, String appId, String appSecret) {
        this.server = server;
        this.configUrl = configUrl;
        this.submit = submit;
        this.appId = appId;
        this.appSecret = appSecret;
        setupHttpClient();
        configBtnNetwork();
    }

    public void configBtnNetwork() {
        abilities.values().forEach(BtnAbility::unload);
        abilities.clear();
        try {
            HttpResponse<String> resp = HTTPUtil.retryableSend(httpClient, MutableRequest.GET(configUrl), HttpResponse.BodyHandlers.ofString()).join();
            if (resp.statusCode() != 200) {
                log.warn(Lang.BTN_CONFIG_FAILS, resp.statusCode() + " - " + resp.body());
                return;
            }
            JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
            if (!json.has("min_protocol_version")) {
                throw new IllegalStateException("Server config response missing min_protocol_version field");
            }
            int min_protocol_version = json.get("min_protocol_version").getAsInt();
            if (min_protocol_version > BTN_PROTOCOL_VERSION) {
                throw new IllegalStateException(String.format(Lang.BTN_INCOMPATIBLE_SERVER));
            }
            int max_protocol_version = json.get("max_protocol_version").getAsInt();
            if (max_protocol_version > BTN_PROTOCOL_VERSION) {
                throw new IllegalStateException(String.format(Lang.BTN_INCOMPATIBLE_SERVER));
            }
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
        } catch (Throwable e) {
            log.warn(Lang.BTN_CONFIG_FAILS, e);
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
        log.info(Lang.BTN_SHUTTING_DOWN);
        executeService.shutdown();
        abilities.values().forEach(BtnAbility::unload);
        abilities.clear();
    }
}
