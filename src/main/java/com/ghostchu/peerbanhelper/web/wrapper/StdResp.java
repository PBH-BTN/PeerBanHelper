package com.ghostchu.peerbanhelper.web.wrapper;

import lombok.Data;
import org.jetbrains.annotations.Nullable;

@Data
public class StdResp {
    private final boolean success;
    private final @Nullable String message;
    private final @Nullable Object data;

    public StdResp(boolean success, @Nullable String message, @Nullable Object data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    public StdResp(boolean success, @Nullable String message, boolean showLogs) {
        this.success = success;
        this.message = message;
        this.data = null;
    }
}
