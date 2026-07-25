package com.ghostchu.peerbanhelper.util.dns;

import com.ghostchu.peerbanhelper.util.dns.impl.PTRLookup;
import org.jetbrains.annotations.NotNull;

public class DNSManImpl implements DNSMan{
    @Override
    public @NotNull DNSLookup get(@NotNull DNSRoute route) {
        return null;
    }

    @Override
    public @NotNull PTRLookup getPtr(@NotNull DNSRoute route) {
        return null;
    }
}
