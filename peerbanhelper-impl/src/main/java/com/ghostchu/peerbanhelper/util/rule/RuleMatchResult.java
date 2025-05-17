package com.ghostchu.peerbanhelper.util.rule;

import com.ghostchu.peerbanhelper.api.text.TranslationComponent;
import org.jetbrains.annotations.Nullable;

public record RuleMatchResult(boolean hit, Rule rule, @Nullable TranslationComponent comment) {
}
