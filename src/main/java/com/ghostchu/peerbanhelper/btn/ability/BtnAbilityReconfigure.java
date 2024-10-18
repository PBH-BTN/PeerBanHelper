package com.ghostchu.peerbanhelper.btn.ability;

import com.ghostchu.peerbanhelper.btn.BtnNetwork;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.Response;

import java.util.concurrent.ThreadLocalRandom;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public class BtnAbilityReconfigure extends AbstractBtnAbility {
    private final BtnNetwork btnNetwork;
    private final long interval;
    private final long randomInitialDelay;
    private final String version;
    private boolean lastStatus;
    private String lastErrorMsg;

    public BtnAbilityReconfigure(BtnNetwork btnNetwork, JsonObject ability) {
        this.btnNetwork = btnNetwork;
        this.interval = ability.get("interval").getAsLong();
        this.randomInitialDelay = ability.get("random_initial_delay").getAsLong();
        this.version = ability.get("version").getAsString();
    }

    @Override
    public void load() {
        setLastStatus(true, "Stand by");
        btnNetwork.getExecuteService().scheduleWithFixedDelay(this::checkIfReconfigure, interval + ThreadLocalRandom.current().nextLong(randomInitialDelay), interval, TimeUnit.MILLISECONDS);
    }

    private void checkIfReconfigure() {
        JsonObject json;
        try (Response resp = HTTPUtil.retryableSend(btnNetwork.getHttpClient(), new Request.Builder().url(btnNetwork.getConfigUrl()).build()).join()) {
            String body = resp.body().string();
            if (resp.code() != 200) {
                setLastStatus(false, "HTTP Error: " + resp.code() + " - " + body);
                log.error(tlUI(Lang.BTN_RECONFIGURE_CHECK_FAILED, resp.code() + " - " + body));
                return;
            }
            json = JsonParser.parseString(body).getAsJsonObject();
        } catch (IOException e) {
            setLastStatus(false, "IO Error");
            return;
        }
        JsonObject ability = json.get("ability").getAsJsonObject();
        if (!ability.has("reconfigure")) {
            setLastStatus(true, "Disabled Reconfigure");
            return;
        }
        JsonObject reconfigure = ability.get("reconfigure").getAsJsonObject();
        setLastStatus(true, "Detected new version, preparing for reconfigure");
        if (!reconfigure.get("version").getAsString().equals(this.version)) {
            log.info(tlUI(Lang.BTN_RECONFIGURING));
            setLastStatus(true, "Reconfiguring");
            btnNetwork.configBtnNetwork();
            setLastStatus(true, "Reconfigured");
        }
    }

    @Override
    public void unload() {

    }
}
