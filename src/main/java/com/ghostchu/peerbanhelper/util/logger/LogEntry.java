package com.ghostchu.peerbanhelper.util.logger;

import org.slf4j.event.Level;

public record LogEntry(Level level, String content) {
    @Override
    public String toString() {
        return content;
    }
}