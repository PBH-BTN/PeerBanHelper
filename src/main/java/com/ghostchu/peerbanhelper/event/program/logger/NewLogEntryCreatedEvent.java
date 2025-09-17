package com.ghostchu.peerbanhelper.event.program.logger;

import com.ghostchu.peerbanhelper.util.logger.LogEntry;

public record NewLogEntryCreatedEvent(LogEntry entry) {
}
