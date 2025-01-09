package com.ghostchu.peerbanhelper.btn.ability;

import com.ghostchu.peerbanhelper.btn.BtnNetwork;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.github.mizosoft.methanol.MutableRequest;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;

import java.net.http.HttpResponse;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public final class BtnAbilityReconfigure extends AbstractBtnAbility {
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
    public String getName() {
        return "BtnAbilityReconfigure";
    }

    @Override
    public TranslationComponent getDisplayName() {
        return new TranslationComponent(Lang.BTN_ABILITY_RECONFIGURE);
    }

    @Override
    public TranslationComponent getDescription() {
        return new TranslationComponent(Lang.BTN_ABILITY_RECONFIGURE_DESCRIPTION);
    }

    @Override
    public void load() {
        setLastStatus(true, new TranslationComponent(Lang.BTN_STAND_BY));
        btnNetwork.getExecuteService().scheduleWithFixedDelay(this::checkIfReconfigure, interval + ThreadLocalRandom.current().nextLong(randomInitialDelay), interval, TimeUnit.MILLISECONDS);
    }

    private void checkIfReconfigure() {
        HttpResponse<String> resp = HTTPUtil.retryableSend(btnNetwork.getHttpClient(), MutableRequest.GET(btnNetwork.getConfigUrl()), HttpResponse.BodyHandlers.ofString()).join();
        if (resp.statusCode() != 200) {
            setLastStatus(false, new TranslationComponent(Lang.BTN_HTTP_ERROR, resp.statusCode(), resp.body()));
            log.error(tlUI(Lang.BTN_RECONFIGURE_CHECK_FAILED, resp.statusCode() + " - " + resp.body()));
            return;
        }
        JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
        JsonObject ability = json.get("ability").getAsJsonObject();
        if (!ability.has("reconfigure")) {
            setLastStatus(true, new TranslationComponent(Lang.BTN_RECONFIGURE_DISABLED_BY_SERVER));
            return;
        }
        JsonObject reconfigure = ability.get("reconfigure").getAsJsonObject();
        if (!reconfigure.get("version").getAsString().equals(this.version)) {
            log.info(tlUI(Lang.BTN_RECONFIGURING));
            setLastStatus(true, new TranslationComponent(Lang.BTN_RECONFIGURE_PREPARE_RECONFIGURE));
            btnNetwork.configBtnNetwork();
            setLastStatus(true, new TranslationComponent(Lang.BTN_STAND_BY));
        }
    }

    @Override
    public void unload() {

    }
}
