package com.ghostchu.peerbanhelper.util.rule;

import com.ghostchu.peerbanhelper.text.TranslationComponent;
import org.jetbrains.annotations.Nullable;

public record MatchResult(MatchResultEnum result, @Nullable TranslationComponent comment) {

}
