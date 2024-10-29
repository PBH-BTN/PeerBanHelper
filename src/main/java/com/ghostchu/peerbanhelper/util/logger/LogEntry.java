package com.ghostchu.peerbanhelper.util.logger;

import org.slf4j.event.Level;

public record LogEntry(long time, String thread, Level level, String content, long seq) {
    @Override
    public String toString() {
        return content;
    }
}