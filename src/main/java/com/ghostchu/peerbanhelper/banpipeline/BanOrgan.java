package com.ghostchu.peerbanhelper.banpipeline;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Slf4j
public abstract class BanOrgan<IN, OUT> {
    protected final BanOrgan<?, IN> in;
    protected final BiConsumer<BanOrgan<IN, OUT>, BanOrganCallback<IN>> gastroscopy;
    protected final ConcurrentMap<CompletableFuture<?>, PipelineTask<?>> runningTasks = new ConcurrentHashMap<>();
    protected final BlockingQueue<OUT> outlet = new ArrayBlockingQueue<>(64);
    protected final Executor digestEnergy;
    protected final long maxDigestDuration;
    protected final TimeUnit digestTimeUnit;
    protected final AtomicBoolean loopRunning = new AtomicBoolean(true);
    protected final AtomicLong lastTick = new AtomicLong(0);

    /**
     * The organ that digest the data from BitTorrent clients. Which connects each other to a pipeline from HEAD to TAIL.
     *
     * @param schedEnergy       The executor that used for schedule
     * @param digestEnergy      The executor that used for digesting the data
     * @param in                Upstream that provide the prey to digesting
     * @param gastroscopy       Gastroscopy that monitoring and process any exception the status changes
     * @param maxDigestDuration The max duration for digesting a prey, if the digestion time exceed this duration, the digestion will be cancelled and consider as TIMEOUT. This is important to avoid the organ stuck by some prey that hard to digest.
     * @param digestTimeUnit    Timeunit.
     */
    public BanOrgan(Executor schedEnergy, Executor digestEnergy, @Nullable BanOrgan<?, IN> in, @Nullable BiConsumer<BanOrgan<IN, OUT>,
            BanOrganCallback<IN>> gastroscopy, long maxDigestDuration, TimeUnit digestTimeUnit) {
        this.in = in;
        this.digestEnergy = digestEnergy;
        this.gastroscopy = gastroscopy;
        this.maxDigestDuration = maxDigestDuration;
        this.digestTimeUnit = digestTimeUnit;
        schedEnergy.execute(this::waitForPrey);
    }

    public void waitForPrey() {
        if (in == null) {
            loopRunning.set(false); // The HEAD organ doesn't have IN source, so just skip it.
            return;
        }
        while (in.getStatus() != OrganLifeCycleStatus.DONE || !checkIfRunningTaskEmpty()) { // If prev organ not stopped (e,g outlet have stuff or stomach still have task running, keep poll it.)
            // if prev organ's stomach still have contents, we need keep fetching its buffer
            try {
                lastTick.set(System.currentTimeMillis());
                IN prey = in.outlet.poll(5, TimeUnit.MILLISECONDS); // set a 5ms delay to avoid spam the CPU, need test if 10ms is good too so we can let CPU sleep more times
                if (prey == null) continue;
                PipelineTask<Void> wrapper = new PipelineTask<>(null, this, "Initializing Task");
                wrapper.setDelegate(CompletableFuture.runAsync(() -> {
                            digest(prey, (excretions) -> {
                                /* The code that outlet.accept actually run */
                                BanOrganCallback<IN> callback = new BanOrganCallback<>(prey, BanOrganCallbackResult.SUCCESS, null, null);
                                try {
                                    outlet.put(excretions);
                                } catch (InterruptedException e) {
                                    callback = new BanOrganCallback<>(prey, BanOrganCallbackResult.TIMEOUT, e, null);
                                    Thread.currentThread().interrupt();
                                } finally {
                                    if (gastroscopy != null) gastroscopy.accept(this, callback);
                                }

                            }, wrapper);
                            wrapper.setComment(false, "Completed.");
                        }, digestEnergy) // run digest executor.
                        .orTimeout(maxDigestDuration, digestTimeUnit) // this is the actually reason we use CompletableFutures
                        .exceptionally(e -> { // f.
                            BanOrganCallback<IN> callback;
                            if (e instanceof InterruptedException || e instanceof TimeoutException) {
                                callback = new BanOrganCallback<>(prey, BanOrganCallbackResult.TIMEOUT, e, null);
                            } else {
                                callback = new BanOrganCallback<>(prey, BanOrganCallbackResult.ERRORED, e, null);
                            }
                            if (gastroscopy != null) gastroscopy.accept(this, callback);
                            return null;
                        }));
                addMoreDigestingPrey(wrapper);
            } catch (InterruptedException e) {
                break;
            }
        }
        loopRunning.set(false); // mark loop exit so status can reflect correct state
    }

    public OrganLifeCycleStatus getStatus() {
        boolean polling = loopRunning.get();
        boolean runningTaskEmpty = checkIfRunningTaskEmpty();
        if (polling) { // waitForPrey() is RUNNING?
            if (runningTaskEmpty) {
                return OrganLifeCycleStatus.WAITING_UPSTREAM; // Waiting upstream organ for more data.
            } else {
                return OrganLifeCycleStatus.RUNNING; // Current organ have their tasks to run.
            }
        } else {
            if (runningTaskEmpty) {
                if (isOutletEmpty()) {
                    return OrganLifeCycleStatus.DONE; // No possible data will come from upstream, no more tasks running, and outlet also empty. nice.
                } else {
                    return OrganLifeCycleStatus.WAITING_OUTLET; // Waiting downstream organ retrieve the data in outlet that already digested. Then this organ can DONE.
                }
            } else {
                return OrganLifeCycleStatus.RUNNING; // although waitForPrey() is not running, but there are still tasks running, so we consider it as RUNNING. Sub-tasks in digest() can lead to this status.
            }
        }
    }

    public void addMoreDigestingPrey(PipelineTask<?> future) {
        var completion = java.util.Objects.requireNonNull(future.getDelegate());
        runningTasks.put(completion, future);
        completion.whenComplete((_, _) -> runningTasks.remove(completion));
    }

    /**
     * Handle and processing the input data, put processed data into outlet.
     *
     * @param input           The input data(s)
     * @param outlet          The data need transfer to downstream organ.
     * @param futureContainer The wrapper Future container so you can set comment and bind it
     * @throws RuntimeException If you throw a RuntimeException, the pipeline will be cancelled at this stage.
     */
    public abstract void digest(IN input, Consumer<OUT> outlet, PipelineTask<?> futureContainer) throws RuntimeException;

    public boolean checkIfRunningTaskEmpty() {
        return runningTasks.isEmpty();
    }

    /**
     * Check if outlet nothing need to retrieve by downstream organ
     *
     * @return Outlet is empty
     */
    public boolean isOutletEmpty() {
        return outlet.isEmpty();
    }

    /**
     * Callback that session is closing
     */
    public void endSession() {
        if (in == null) return;
        in.endSession();
    }
}
