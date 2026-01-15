package com.ghostchu.peerbanhelper.platform;

import com.ghostchu.peerbanhelper.exchange.ExchangeMap;
import com.ghostchu.peerbanhelper.platform.impl.win32.ecoqos.EcoMode;
import com.ghostchu.peerbanhelper.platform.types.EcoQosAPI;
import com.ghostchu.peerbanhelper.text.Lang;
import lombok.extern.slf4j.Slf4j;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public final class WindowsEcoQosAPI implements EcoQosAPI {
    @Override
    public void apply() throws UnsupportedOperationException {
        try {
            EcoMode ecoMode = new EcoMode();
            ecoMode.setPriority(6);
            String result = ecoMode.setEcoMode(true);
            if (!"SUCCESS".equalsIgnoreCase(result)) {
                throw new IllegalStateException(result);
            }
            log.info(tlUI(Lang.IN_ECOMODE_DESCRIPTION));
            ExchangeMap.GUI_DISPLAY_FLAGS.add(new ExchangeMap.DisplayFlag("eco-mode", 10, tlUI(Lang.IN_ECOMODE_SHORT)));
        }catch (Exception e) {
            log.warn("Unable to apply Windows EcoQosAPI feature", e);
        }
    }
}
