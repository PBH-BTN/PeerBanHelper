package com.ghostchu.peerbanhelper.btn.ability;

import com.ghostchu.peerbanhelper.btn.BtnNetwork;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

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
        btnNetwork.getExecuteService().scheduleWithFixedDelay(this::checkIfReconfigure, interval + new Random().nextLong(randomInitialDelay), interval, TimeUnit.MILLISECONDS);
    }

    private void checkIfReconfigure() {
        JsonObject json;
        try (Response resp = HTTPUtil.retryableSend(btnNetwork.getHttpClient(), new Request.Builder().url(btnNetwork.getConfigUrl()).build()).join()) {
            if (resp.code() != 200) {
                log.error(tlUI(Lang.BTN_RECONFIGURE_CHECK_FAILED, resp.code() + " - " + resp.body().string()));
                return;
            }
            json = JsonParser.parseString(resp.body().string()).getAsJsonObject();
        } catch (IOException e) {
            return;
        }
        JsonObject ability = json.get("ability").getAsJsonObject();
        if (!ability.has("reconfigure")) {
            return;
        }
        JsonObject reconfigure = ability.get("reconfigure").getAsJsonObject();
        if (!reconfigure.get("version").getAsString().equals(this.version)) {
            log.info(tlUI(Lang.BTN_RECONFIGURING));
            btnNetwork.configBtnNetwork();
        }
    }

    @Override
    public void unload() {

    }
}
