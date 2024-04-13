package com.ghostchu.peerbanhelper.btn;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.JsonUtil;
import lombok.*;
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
    private final String pingUrl;
    @Getter
    private final BtnNetwork network;
    private final boolean submit;
    private ScheduledExecutorService executeService = Executors.newScheduledThreadPool(1);
    private BtnConfig btnConfig;

    @SneakyThrows(IOException.class)
    public BtnManager(PeerBanHelperServer server, ConfigurationSection section) {
        this.server = server;
        if (!section.getBoolean("enabled")) {
            throw new IllegalStateException("BTN has been disabled");
        }
        this.pingUrl = section.getString("url");
        this.submit = section.getBoolean("submit");
        this.network = new BtnNetwork(this, section.getString("url"), section.getString("app-id"), section.getString("app-secret"), submit);
        File file = new File(Main.getDataDirectory(), "btn.cache");
        if (!file.exists()) {
            if(!file.getParentFile().exists()){
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
                    .send(HttpRequest.newBuilder(new URI(pingUrl + "/config"))
                            .GET()
                            .header("User-Agent", Main.getUserAgent())
                            .header("Content-Type", "application/json")
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
            executeService.shutdownNow();
            executeService = Executors.newScheduledThreadPool(2);
            Random random = new Random();
            executeService.schedule(network::update, random.nextLong(btnConfig.getDelayRandomRange()), TimeUnit.MILLISECONDS);
            executeService.schedule(network::ping, random.nextLong(btnConfig.getDelayRandomRange()), TimeUnit.MILLISECONDS);
            log.info(Lang.BTN_NETWORK_RECONFIGURED, btnConfig);
        } catch (Throwable e) {
            log.warn(Lang.BTN_CONFIG_FAILS, e);
        }
    }

    public void close() {
        executeService.shutdownNow();
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class BtnConfig {
        private long delayRandomRange;
        private long submitPeriod;
        private long batchPeriod;
    }
}
