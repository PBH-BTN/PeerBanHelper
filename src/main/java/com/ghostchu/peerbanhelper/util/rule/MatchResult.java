package com.ghostchu.peerbanhelper.util.rule;

import org.jetbrains.annotations.Nullable;

public record MatchResult(MatchResultEnum result, @Nullable String comment) {

}
