package com.ghostchu.peerbanhelper.banpipeline;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

@EqualsAndHashCode
public class PipelineTask<T> {
    private final BanOrgan<?, ?> organ;
    @Getter
    @Nullable
    private volatile String comment;
    @Setter
    @Nullable
    private CompletableFuture<T> delegate;
    @Getter
    private volatile boolean io;

    public PipelineTask(CompletableFuture<T> delegate, BanOrgan<?, ?> organ, String comment) {
        this.delegate = delegate;
        this.organ = organ;
        this.comment = comment;
    }

    public void setComment(boolean io, @Nullable String comment) {
        this.io = io;
        this.comment = comment;
    }

    @Override
    public String toString() {
        return "[Task] (" + organ.getClass().getSimpleName() + ") " + comment;
    }
}
