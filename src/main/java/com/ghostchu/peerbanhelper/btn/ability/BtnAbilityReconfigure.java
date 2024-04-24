package com.ghostchu.peerbanhelper.btn.ability;

import com.ghostchu.peerbanhelper.btn.BtnNetwork;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.github.mizosoft.methanol.MutableRequest;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;

import java.net.http.HttpResponse;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Slf4j
public class BtnAbilityReconfigure implements BtnAbility {
    private final BtnNetwork btnNetwork;
    private final long interval;
    private final long randomInitialDelay;
    private final String version;

    public BtnAbilityReconfigure(BtnNetwork btnNetwork, JsonObject ability) {
        this.btnNetwork = btnNetwork;
        this.interval = ability.get("interval").getAsLong();
        this.randomInitialDelay = ability.get("random_initial_delay").getAsLong();
        this.version = ability.get("version").getAsString();
    }

    @Override
    public void load() {
        btnNetwork.getExecuteService().scheduleAtFixedRate(this::checkIfReconfigure, interval + new Random().nextLong(randomInitialDelay), interval, TimeUnit.MILLISECONDS);
    }

    private void checkIfReconfigure() {
        HttpResponse<String> resp = HTTPUtil.retryableSend(btnNetwork.getHttpClient(), MutableRequest.GET(btnNetwork.getConfigUrl()), HttpResponse.BodyHandlers.ofString()).join();
        if (resp.statusCode() != 200) {
            log.warn(Lang.BTN_RECONFIGURE_CHECK_FAILED, resp.statusCode() + " - " + resp.body());
            return;
        }
        JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
        JsonObject ability = json.get("ability").getAsJsonObject();
        if (!ability.has("reconfigure")) {
            return;
        }
        JsonObject reconfigure = ability.get("reconfigure").getAsJsonObject();
        if (!reconfigure.get("version").getAsString().equals(this.version)) {
            log.info(Lang.BTN_RECONFIGURING);
            btnNetwork.getServer().setupBtn();
        }
    }

    @Override
    public void unload() {

    }
}
