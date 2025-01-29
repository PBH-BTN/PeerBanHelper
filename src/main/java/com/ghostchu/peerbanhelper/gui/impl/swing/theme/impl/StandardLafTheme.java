package com.ghostchu.peerbanhelper.gui.impl.swing.theme.impl;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.ghostchu.peerbanhelper.gui.impl.swing.theme.PBHFlatLafTheme;

public final class StandardLafTheme implements PBHFlatLafTheme {
    @Override
    public void applyDark() {
        FlatDarculaLaf.setup();
    }

    @Override
    public void applyLight() {
        FlatIntelliJLaf.setup();
    }
}
