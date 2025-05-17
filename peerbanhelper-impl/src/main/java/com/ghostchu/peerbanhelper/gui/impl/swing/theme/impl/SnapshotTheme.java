package com.ghostchu.peerbanhelper.gui.impl.swing.theme.impl;

import com.formdev.flatlaf.intellijthemes.FlatSolarizedDarkIJTheme;
import com.formdev.flatlaf.intellijthemes.FlatSolarizedLightIJTheme;
import com.ghostchu.peerbanhelper.gui.impl.swing.theme.PBHFlatLafTheme;

public final class SnapshotTheme implements PBHFlatLafTheme {
    @Override
    public void applyDark() {
        FlatSolarizedDarkIJTheme.setup();
    }

    @Override
    public void applyLight() {
        FlatSolarizedLightIJTheme.setup();
    }
}
