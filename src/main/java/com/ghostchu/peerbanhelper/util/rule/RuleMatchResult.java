package com.ghostchu.peerbanhelper.util.rule;

import org.jetbrains.annotations.Nullable;

public record RuleMatchResult(boolean hit, Rule rule, @Nullable String comment) {
}
