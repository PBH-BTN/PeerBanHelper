package com.ghostchu.peerbanhelper.platform;

import com.ghostchu.lib.jni.EcoMode;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.exchange.ExchangeMap;
import com.ghostchu.peerbanhelper.text.Lang;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.springframework.stereotype.Component;

import java.util.Locale;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Component
@Slf4j
public class WindowsEcoQosAPI {
    private final YamlConfiguration config;
    private final EcoMode ecoMode;

    public WindowsEcoQosAPI(EcoMode ecoMode) {
        this.ecoMode = ecoMode;
        this.config = Main.getMainConfig();
        if (this.config.getBoolean("performance.windows-ecoqos-api")) {
            installEcoQosApi();
        }
    }

    /**
     * Installs and configures Windows Eco QoS API for power-efficient system performance.
     *
     * This method checks if the current operating system is Windows and attempts to enable eco mode.
     * When successful, it logs an informational message and adds a display flag to indicate eco mode status.
     *
     * @implNote Only activates on Windows operating systems
     * @implSpec Relies on {@code ecoMode.ecoMode(true)} to manage eco mode activation
     */
    private void installEcoQosApi() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (os.startsWith("win")) {
            if (ecoMode.ecoMode(true)) {
                log.info(tlUI(Lang.IN_ECOMODE_DESCRIPTION));
                ExchangeMap.GUI_DISPLAY_FLAGS.add(new ExchangeMap.DisplayFlag("eco-mode", 10, tlUI(Lang.IN_ECOMODE_SHORT)));
            }
        }
    }
}
