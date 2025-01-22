package com.ghostchu.peerbanhelper.gui.impl.swing.theme.impl;

import com.formdev.flatlaf.intellijthemes.FlatSolarizedDarkIJTheme;
import com.formdev.flatlaf.intellijthemes.FlatSolarizedLightIJTheme;
import com.ghostchu.peerbanhelper.gui.impl.swing.theme.PBHFlatLafTheme;

public class SnapshotTheme implements PBHFlatLafTheme {
    @Override
    public void applyDark() {
        FlatSolarizedLightIJTheme.setup();
    }

    @Override
    public void applyLight() {
        FlatSolarizedDarkIJTheme.setup();
    }
}
