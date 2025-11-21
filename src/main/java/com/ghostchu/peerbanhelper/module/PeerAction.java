package com.ghostchu.peerbanhelper.module;

import lombok.Getter;

@Getter
public enum PeerAction {
    NO_ACTION(0),
    BAN(1),
    BAN_FOR_DISCONNECT(2),
    SKIP(3);
    private final int value;

    PeerAction(int value) {
        this.value = value;
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
