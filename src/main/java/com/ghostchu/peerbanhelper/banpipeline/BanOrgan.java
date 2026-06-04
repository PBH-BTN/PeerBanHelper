package com.ghostchu.peerbanhelper.banpipeline;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

public abstract class BanOrgan<IN, OUT> {
    private final BanOrgan<?, IN> in;
    private final BiConsumer<BanOrgan<IN, OUT>, BanOrganCallback> gastroscopy;
    private final LinkedBlockingQueue<OUT> buffer = new LinkedBlockingQueue<>();
    private final Executor digestEnergy;
    private final long maxDigestDuration;
    private final TimeUnit digestTimeUnit;

    public BanOrgan(Executor schedEnergy, Executor digestEnergy, BanOrgan<?, IN> in, BiConsumer<BanOrgan<IN, OUT>,
            BanOrganCallback> gastroscopy, long maxDigestDuration, TimeUnit digestTimeUnit) {
        this.in = in;
        this.digestEnergy = digestEnergy;
        this.gastroscopy = gastroscopy;
        this.maxDigestDuration = maxDigestDuration;
        this.digestTimeUnit = digestTimeUnit;
        schedEnergy.execute(this::waitForFood);
    }

    public void waitForFood() {
        while (!in.isStomachEmpty()) {
            try {
                IN food = in.buffer.poll(10, TimeUnit.MILLISECONDS);
                if (food == null) continue;
                try {
                    OUT excretions = CompletableFuture.supplyAsync(() -> digest(food), digestEnergy)
                            .get(maxDigestDuration, digestTimeUnit);
                    buffer.offer(excretions);
                } catch (ExecutionException e) {
                    BanOrganCallback callback = new BanOrganCallback(
                            BanOrganCallbackResult.ERRORED, e, null);
                    gastroscopy.accept(this, callback);
                } catch (TimeoutException e) {
                    BanOrganCallback callback = new BanOrganCallback(
                            BanOrganCallbackResult.TIMEOUT, e, null);
                    gastroscopy.accept(this, callback);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public abstract OUT digest(IN input) throws RuntimeException;

    public boolean isStomachEmpty() {
        return in.isStomachEmpty();
    }
}
