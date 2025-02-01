package com.ghostchu.peerbanhelper.wrapper;

import com.ghostchu.peerbanhelper.module.PeerAction;

public enum BanBehavior {
    BAN,
    DISCONNECT,
    THROTTLE;

    public static BanBehavior fromPeerAction(PeerAction peerAction) {
        return switch (peerAction) {
            case BAN -> BAN;
            case BAN_FOR_DISCONNECT -> DISCONNECT;
            case THROTTLE -> THROTTLE;
            case NO_ACTION, SKIP -> null;
        };
    }
}
