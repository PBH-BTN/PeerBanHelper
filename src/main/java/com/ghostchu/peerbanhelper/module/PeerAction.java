package com.ghostchu.peerbanhelper.module;

import lombok.Getter;

@Getter
public enum PeerAction {
    NO_ACTION(0, false, false),
    BAN_FOR_DISCONNECT(1, true, true),
    BAN(2, false, false),
    SKIP(3, false, false);
    private final int value;
    private final boolean excludeFromReport;
    private final boolean excludeFromDisplay;

    PeerAction(int value, boolean excludeFromReport, boolean excludeFromDisplay) {
        this.value = value;
        this.excludeFromReport = excludeFromReport;
        this.excludeFromDisplay = excludeFromDisplay;
    }

    public static PeerAction fromValue(int value) {
        for (PeerAction action : PeerAction.values()) {
            if (action.value == value) {
                return action;
            }
        }
        throw new IllegalArgumentException("Unknown PeerAction value: " + value);
    }

}
