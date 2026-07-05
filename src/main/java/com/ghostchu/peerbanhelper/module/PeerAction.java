package com.ghostchu.peerbanhelper.module;

import lombok.Getter;

@Getter
public enum PeerAction {
    NO_ACTION(0, false,false,false, false),
    BAN_FOR_DISCONNECT(1, true, true,true, true),
    BAN(2,false,false, false, false),
    SKIP(3, false,false,false, false);
    private final int value;
    private final boolean excludeFromPersist;
    private final boolean excludeFromNotify;

    private final boolean excludeFromReport;
    private final boolean excludeFromDisplay;

    PeerAction(int value, boolean excludeFromPersist, boolean excludeFromNotify, boolean excludeFromReport, boolean excludeFromDisplay) {
        this.value = value;
        this.excludeFromPersist = excludeFromPersist;
        this.excludeFromNotify = excludeFromNotify;
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
