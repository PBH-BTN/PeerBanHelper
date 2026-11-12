package com.ghostchu.peerbanhelper.platform.impl.linux.firewall;

import java.util.OptionalLong;
import java.util.UUID;

/**
 * Naming and stale-namespace parsing for PeerBanHelper-owned nftables tables.
 */
final class NftablesNamespace {
    static final String PREFIX = "peerbanhelper_firewall_";

    private NftablesNamespace() {
    }

    static String create(long pid) {
        if (pid <= 0) {
            throw new IllegalArgumentException("pid must be positive");
        }
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return PREFIX + pid + "_" + token;
    }

    static OptionalLong ownerPid(String tableName) {
        if (tableName == null || !tableName.startsWith(PREFIX)) {
            return OptionalLong.empty();
        }
        int pidEnd = tableName.indexOf('_', PREFIX.length());
        if (pidEnd < 0 || pidEnd == PREFIX.length()) {
            return OptionalLong.empty();
        }
        String pidText = tableName.substring(PREFIX.length(), pidEnd);
        String token = tableName.substring(pidEnd + 1);
        if (!token.matches("[0-9a-f]{12}")) {
            return OptionalLong.empty();
        }
        try {
            long pid = Long.parseLong(pidText);
            return pid > 0 ? OptionalLong.of(pid) : OptionalLong.empty();
        } catch (NumberFormatException ignored) {
            return OptionalLong.empty();
        }
    }
}
