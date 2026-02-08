package com.ghostchu.peerbanhelper.pbhplus;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.exchange.ExchangeMap;
import com.ghostchu.peerbanhelper.pbhplus.backend.LicenseBackend;
import com.ghostchu.peerbanhelper.pbhplus.bean.License;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import com.google.common.hash.Hashing;
import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
@Component
public class LicenseManager implements Reloadable {
    private final LicenseBackend licenseBackend;
    private final LicenseParser licenseParser;

    public LicenseManager(@Qualifier("revolvableLicenseBackend") LicenseBackend licenseBackend, LicenseParser parser) {
        this.licenseBackend = licenseBackend;
        this.licenseParser = parser;
        load();
    }

    private void load() {
        var keyTexts = Main.getMainConfig().getStringList("pbh-plus-key");
        Map<String, License> licenseList = new LinkedHashMap<>();
        var keyIterator = keyTexts.iterator();
        while (keyIterator.hasNext()) {
            var key = keyIterator.next().trim();
            try {
                var license = licenseParser.fromLicense(key);
                licenseList.put(Hashing.sha256().hashString(key, StandardCharsets.UTF_8).toString(), license);
            } catch (Exception e) {
                log.warn(tlUI(Lang.PBH_LICENSE_PARSE_FAILED, e.getClass().getName() + ": " + e.getMessage()), e);
                keyIterator.remove();
                Sentry.captureException(e);
            }
        }
        Main.getMainConfig().set("pbh-plus-key", keyTexts);
        try {
            Main.getMainConfig().save(Main.getMainConfigFile());
        } catch (IOException e) {
            log.error("Unable to save main configuration file!", e);
            Sentry.captureException(e);
        }
        licenseBackend.setLicenses(licenseList);
        if (isFeatureEnabled("basic")) {
            ExchangeMap.GUI_DISPLAY_FLAGS.add(new ExchangeMap.DisplayFlag("pbh-plus", 0, tlUI(Lang.PBH_PLUS_THANKS_FOR_DONATION_GUI_TITLE)));
            ExchangeMap.PBH_PLUS_ACTIVATED = true;
        } else {
            ExchangeMap.PBH_PLUS_ACTIVATED = false;
            ExchangeMap.GUI_DISPLAY_FLAGS.removeIf(displayFlag -> "pbh-plus".equals(displayFlag.getId()));
        }
    }

    public boolean isFeatureEnabled(@NotNull String feature) {
        return licenseBackend.isFeatureEnabled(feature);
    }

    @NotNull
    public LicenseBackend getLicenseBackend() {
        return licenseBackend;
    }

    @NotNull
    public LicenseParser getLicenseParser() {
        return licenseParser;
    }

    @Override
    public ReloadResult reloadModule() throws Exception {
        load();
        return Reloadable.super.reloadModule();
    }
}
