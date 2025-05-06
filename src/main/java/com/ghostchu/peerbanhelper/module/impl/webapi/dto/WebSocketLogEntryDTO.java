package com.ghostchu.peerbanhelper.module.impl.webapi.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class WebSocketLogEntryDTO {
    private long time;
    private String thread;
    private String level;
    private String content;
    private long offset;
}
