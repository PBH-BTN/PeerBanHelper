package com.ghostchu.peerbanhelper.databasent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrafficDataComputed {
    private OffsetDateTime timestamp;
    private long dataOverallUploaded;
    private long dataOverallDownloaded;
}
