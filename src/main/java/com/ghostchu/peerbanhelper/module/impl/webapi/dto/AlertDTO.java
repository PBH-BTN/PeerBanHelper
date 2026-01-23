package com.ghostchu.peerbanhelper.module.impl.webapi.dto;

import com.ghostchu.peerbanhelper.alert.AlertLevel;

import java.time.OffsetDateTime;

public record AlertDTO(Long id, OffsetDateTime createAt, OffsetDateTime readAt, AlertLevel level, String identifier,
                       String title,
                       String content) {

}
