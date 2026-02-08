package com.ghostchu.peerbanhelper.databasent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UniversalFieldDateResult {
    private long timestamp;
    private long count;
    private double percent;
}