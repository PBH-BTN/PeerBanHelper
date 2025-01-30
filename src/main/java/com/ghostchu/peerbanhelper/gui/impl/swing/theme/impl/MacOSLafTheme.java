package com.ghostchu.peerbanhelper.gui.impl.swing.theme.impl;

import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;
import com.ghostchu.peerbanhelper.gui.impl.swing.theme.PBHFlatLafTheme;

public final class MacOSLafTheme implements PBHFlatLafTheme {
    @Override
    public void applyDark() {
        FlatMacDarkLaf.setup();
    }

    @Override
    public void applyLight() {
        FlatMacLightLaf.setup();
    }
}
