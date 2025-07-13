package com.ghostchu.peerbanhelper.util.logger;

import org.jetbrains.annotations.NotNull;
import org.slf4j.event.Level;

public record LogEntry(long time, String thread, Level level, String content, long seq) {
    @Override
    public @NotNull String toString() {
        return content;
    }
}