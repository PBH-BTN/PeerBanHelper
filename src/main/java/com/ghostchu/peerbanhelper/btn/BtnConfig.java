package com.ghostchu.peerbanhelper.btn;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BtnConfig {
    private Ability ability;
    private Threshold threshold;
    private Endpoint endpoint;
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Ability{
        private boolean submit;
        private boolean rule;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Threshold {
        private long delayRandomRange;
        private long ruleUpdatePeriod;
        private long submitPeriod;
        private long batchPeriod;
        private int perBatchSize;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Endpoint {
        private String ping;
        private String rule;
    }
}