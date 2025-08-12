package com.ghostchu.peerbanhelper.util.traversal.stun;

import com.ghostchu.peerbanhelper.text.TranslationComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;

public interface StunListener {
    void onCreate(@NotNull InetSocketAddress inter, @NotNull InetSocketAddress outer);
    void onClose(@Nullable Throwable throwable);

    void onNotApplicable(@NotNull TranslationComponent reason);
}
