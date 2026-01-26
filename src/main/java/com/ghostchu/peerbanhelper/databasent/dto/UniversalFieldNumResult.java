package com.ghostchu.peerbanhelper.databasent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class UniversalFieldNumResult {
    private String data;
    private long count;
    private double percent;
}