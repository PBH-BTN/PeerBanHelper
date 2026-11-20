package com.ghostchu.peerbanhelper.module;

import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.wrapper.StructuredData;
import org.jetbrains.annotations.NotNull;

public record CheckResult(
        @NotNull Class<?> moduleContext,
        @NotNull PeerAction action,
        long duration,
        @NotNull TranslationComponent rule,
        @NotNull TranslationComponent reason,
        @NotNull StructuredData<String, Object> structuredData
) {
    public static CheckResult preferred(CheckResult previous, CheckResult next) {
        if (previous == null) return next;
        int priority = next.action().compareTo(previous.action());
        return priority > 0 || (priority == 0 && next.duration() >= previous.duration()) ? next : previous;
    }
}
