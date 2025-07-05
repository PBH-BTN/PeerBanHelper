package com.ghostchu.peerbanhelper.module.impl.webapi.dto;

import com.ghostchu.peerbanhelper.alert.AlertLevel;

import java.sql.Timestamp;

public record AlertDTO(Long id, Timestamp createAt, Timestamp readAt, AlertLevel level, String identifier, String title,
                       String content) {

}
