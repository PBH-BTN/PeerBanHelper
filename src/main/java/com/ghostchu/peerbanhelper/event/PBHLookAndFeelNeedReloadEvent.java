package com.ghostchu.peerbanhelper.event;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public final class PBHLookAndFeelNeedReloadEvent {
    private boolean darkMode;
}
