package com.ghostchu.peerbanhelper.event;

import com.ghostchu.peerbanhelper.util.logger.LogEntry;

public final class NewLogEntryCreatedEvent {
    private final LogEntry entry;

    public NewLogEntryCreatedEvent(LogEntry entry) {
        this.entry = entry;
    }

    public LogEntry getEntry() {
        return entry;
    }
}
