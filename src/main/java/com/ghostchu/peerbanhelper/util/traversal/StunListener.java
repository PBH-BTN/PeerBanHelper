package com.ghostchu.peerbanhelper.util.traversal;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;

public interface StunListener {
    void onCreate(@NotNull InetSocketAddress inter, @NotNull InetSocketAddress outer);
    void onClose(@Nullable Throwable throwable);
}
