package com.ghostchu.peerbanhelper.web.wrapper;

import org.jetbrains.annotations.Nullable;

public record StdResp(boolean success, @Nullable String message, Object data) {
}
