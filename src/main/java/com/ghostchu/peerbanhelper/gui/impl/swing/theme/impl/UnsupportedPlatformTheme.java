package com.ghostchu.peerbanhelper.gui.impl.swing.theme.impl;

import com.formdev.flatlaf.intellijthemes.FlatHighContrastIJTheme;
import com.ghostchu.peerbanhelper.gui.impl.swing.theme.PBHFlatLafTheme;

public class UnsupportedPlatformTheme implements PBHFlatLafTheme {
    @Override
    public void applyDark() {
        FlatHighContrastIJTheme.setup();
    }

    @Override
    public void applyLight() {
        FlatHighContrastIJTheme.setup();
    }
}
