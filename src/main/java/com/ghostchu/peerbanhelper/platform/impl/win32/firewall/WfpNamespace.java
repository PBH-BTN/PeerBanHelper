package com.ghostchu.peerbanhelper.platform.impl.win32.firewall;

import java.util.UUID;

/**
 * Human-readable namespace strings for WFP objects.
 */
final class WfpNamespace {
    static final String PREFIX = "PeerBanHelper Firewall ";

    private final long pid;
    private final String token;

    WfpNamespace() {
        this.pid = ProcessHandle.current().pid();
        this.token = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    String name(String suffix) {
        return PREFIX + pid + "-" + token + " " + suffix;
    }
}
