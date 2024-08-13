package com.ghostchu.peerbanhelper.event;

import com.ghostchu.peerbanhelper.log4j2.MemoryLoggerAppender;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@Data
public class LoggerEventRecordCreatedEvent {
    private MemoryLoggerAppender.LoggerEventRecord record;
}
