package com.ghostchu.peerbanhelper.util.traversal;

import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class NatAddressProviderRegistry extends CopyOnWriteArrayList<NatAddressProvider> implements NatAddressProvider {

    public @Nullable InetSocketAddress translate(@Nullable InetSocketAddress nattedAddress) {
        InetSocketAddress translated;
        for (NatAddressProvider value : this) {
            translated = value.translate(nattedAddress);
            if (translated != null) {
                return translated;
            }
        }
        return null;
    }

}
