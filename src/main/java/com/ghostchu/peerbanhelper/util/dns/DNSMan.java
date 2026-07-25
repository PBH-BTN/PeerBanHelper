package com.ghostchu.peerbanhelper.util.dns;

import com.ghostchu.peerbanhelper.util.dns.impl.PTRLookup;
import org.jetbrains.annotations.NotNull;

public interface DNSMan {
    @NotNull
    DNSLookup get(@NotNull DNSRoute route);

    @NotNull
    PTRLookup getPtr(@NotNull DNSRoute route);
}
