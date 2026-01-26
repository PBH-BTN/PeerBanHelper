package com.ghostchu.peerbanhelper.event.database;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class SlowQueryEvent {
    private final String sql;
    private final long executionTimeMillis;
}
