package com.ghostchu.peerbanhelper.gui.impl.swing.theme.impl;

import com.formdev.flatlaf.intellijthemes.FlatArcDarkOrangeIJTheme;
import com.formdev.flatlaf.intellijthemes.FlatArcOrangeIJTheme;
import com.ghostchu.peerbanhelper.gui.impl.swing.theme.PBHFlatLafTheme;

public final class PBHPlusTheme implements PBHFlatLafTheme {
    @Override
    public void applyDark() {
        FlatArcDarkOrangeIJTheme.installLafInfo();
        FlatArcDarkOrangeIJTheme.setup();
    }

    @Override
    public void applyLight() {
        FlatArcOrangeIJTheme.setup();
    }
}
