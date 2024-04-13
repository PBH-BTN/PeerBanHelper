package com.ghostchu.peerbanhelper.btn;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.JsonUtil;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class BtnManager {
    @Getter
    private final PeerBanHelperServer server;
    private final String configUrl;
    @Getter
    private final BtnNetwork network;
    private final boolean submit;
    private final String appId;
    private final String appSecret;
    private ScheduledExecutorService executeService = Executors.newScheduledThreadPool(1);
    @Getter
    private BtnConfig btnConfig;

    @SneakyThrows(IOException.class)
    public BtnManager(PeerBanHelperServer server, ConfigurationSection section) {
        this.server = server;
        if (!section.getBoolean("enabled")) {
            throw new IllegalStateException("BTN has been disabled");
        }
        this.configUrl = section.getString("config-url");
        this.submit = section.getBoolean("submit");
        this.appId = section.getString("app-id");
        this.appSecret = section.getString("app-secret");
        this.network = new BtnNetwork(this, appId, appSecret, submit);
        File file = new File(Main.getDataDirectory(), "btn.cache");
        if (!file.exists()) {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            file.createNewFile();
        } else {
            try {
                this.network.setRule(JsonUtil.getGson().fromJson(Files.readString(file.toPath()), BtnRule.class));
            } catch (Throwable ignored) {
            }
        }
        reconfigureExecutor();
    }

    private void reconfigureExecutor() {
        try {
            HttpResponse<String> resp = HTTPUtil.getHttpClient(false, null)
                    .send(HttpRequest.newBuilder(new URI(configUrl))
                            .GET()
                            .header("User-Agent", Main.getUserAgent())
                            .header("Content-Type", "application/json")
                            .header("BTN-AppID", appId)
                            .header("BTN-AppSecret", appSecret)
                            .timeout(Duration.of(30, ChronoUnit.SECONDS))
                            .build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn(Lang.BTN_CONFIG_FAILS, resp.statusCode() + " - " + resp.body());
                return;
            }
            BtnConfig btnConfig = JsonUtil.getGson().fromJson(resp.body(), BtnConfig.class);
            if (btnConfig.equals(this.btnConfig)) {
                return;
            }
            if (!executeService.isShutdown()) {
                executeService.shutdownNow();
            }
            executeService = Executors.newScheduledThreadPool(2);
            Random random = new Random();
            long ruleUpdateOffset = random.nextLong(btnConfig.getThreshold().getDelayRandomRange());
            long pingOffset = random.nextLong(btnConfig.getThreshold().getDelayRandomRange());
            executeService.scheduleAtFixedRate(network::updateRule, ruleUpdateOffset, btnConfig.getThreshold().getRuleUpdatePeriod(), TimeUnit.MILLISECONDS);
            executeService.scheduleAtFixedRate(network::ping, pingOffset, btnConfig.getThreshold().getSubmitPeriod(), TimeUnit.MILLISECONDS);
            log.info(Lang.BTN_NETWORK_RECONFIGURED, btnConfig);
            this.btnConfig = btnConfig;
        } catch (Throwable e) {
            log.warn(Lang.BTN_CONFIG_FAILS, e);
        }
    }

    public void close() {
        executeService.shutdownNow();
    }


}
