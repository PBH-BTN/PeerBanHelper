package com.ghostchu.peerbanhelper.gui.impl.swing.theme.impl;

import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.ghostchu.peerbanhelper.gui.impl.swing.theme.PBHFlatLafTheme;

public class MacOSLafTheme implements PBHFlatLafTheme {
    @Override
    public void applyDark() {
        FlatMacDarkLaf.setup();
    }

    @Override
    public void applyLight() {
        FlatIntelliJLaf.setup();
    }
}
