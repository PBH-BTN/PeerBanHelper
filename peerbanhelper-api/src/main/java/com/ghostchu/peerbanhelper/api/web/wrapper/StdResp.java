package com.ghostchu.peerbanhelper.api.web.wrapper;

import org.jetbrains.annotations.Nullable;

public record StdResp(boolean success, @Nullable String message, Object data) {
}
