package com.ghostchu.peerbanhelper.databasent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class IPAddressTimeSeen {
    private OffsetDateTime firstTimeSeen;
    private OffsetDateTime lastTimeSeen;
}
