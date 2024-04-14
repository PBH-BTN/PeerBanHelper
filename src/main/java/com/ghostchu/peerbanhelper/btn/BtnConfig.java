package com.ghostchu.peerbanhelper.btn;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BtnConfig {
    private List<String> ability;
    private long delayRandomRange;
    private AbilitySubmit abilitySubmit;
    private AbilityRule abilityRule;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AbilitySubmit{
        private String endpoint;
        private long period;
        private long batchPeriod;
        private int perBatchSize;
    }
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AbilityRule{
        private String endpoint;
        private long period;
    }
}