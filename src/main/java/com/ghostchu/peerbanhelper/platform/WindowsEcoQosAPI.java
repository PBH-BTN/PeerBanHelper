package com.ghostchu.peerbanhelper.platform;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.exchange.ExchangeMap;
import com.ghostchu.peerbanhelper.platform.win32.ecoqos.EcoMode;
import com.ghostchu.peerbanhelper.text.Lang;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.springframework.stereotype.Component;

import java.util.Locale;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Component
@Slf4j
public final class WindowsEcoQosAPI {
    private final YamlConfiguration config;

    public WindowsEcoQosAPI() {
        this.config = Main.getMainConfig();
        if (this.config.getBoolean("performance.windows-ecoqos-api")) {
            installEcoQosApi();
        }
    }

    private void installEcoQosApi() {
        try {
            String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
            if (os.startsWith("win")) {
                EcoMode ecoMode = new EcoMode();
                ecoMode.setPriority(6);
                String result = ecoMode.setEcoMode(true);
                if (!"SUCCESS".equalsIgnoreCase(result)) {
                    throw new IllegalStateException(result);
                }
                log.info(tlUI(Lang.IN_ECOMODE_DESCRIPTION));
                ExchangeMap.GUI_DISPLAY_FLAGS.add(new ExchangeMap.DisplayFlag("eco-mode", 10, tlUI(Lang.IN_ECOMODE_SHORT)));
            }
        } catch (Exception e) {
            log.warn(tlUI(Lang.ECOMODE_FAILED, e.getClass().getName() + ": " + e.getMessage()));
        }
    }
}
