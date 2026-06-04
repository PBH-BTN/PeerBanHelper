package com.ghostchu.peerbanhelper.banpipeline;

import lombok.Getter;

import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class DigestionSystem {
    @Getter
    private final UUID sessionId = UUID.randomUUID();
    private final long sessionStartAt = System.currentTimeMillis();
    private Executor executor = Executors.newWorkStealingPool(Runtime.getRuntime().availableProcessors());




}
