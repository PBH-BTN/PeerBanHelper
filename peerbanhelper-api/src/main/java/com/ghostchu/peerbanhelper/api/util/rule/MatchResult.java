package com.ghostchu.peerbanhelper.api.util.rule;

import com.ghostchu.peerbanhelper.api.text.TranslationComponent;
import org.jetbrains.annotations.Nullable;

public record MatchResult(MatchResultEnum result, @Nullable TranslationComponent comment) {

}
