package com.ghostchu.peerbanhelper.invoker;


import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import org.jetbrains.annotations.NotNull;

public interface BanListInvoker {
    void reset();

    void add(@NotNull PeerAddress peer, @NotNull BanMetadata banMetadata);

    void remove(@NotNull PeerAddress peer, @NotNull BanMetadata banMetadata);
}
