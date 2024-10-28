package com.ghostchu.peerbanhelper.util.rule;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@EqualsAndHashCode
@ToString
public abstract class AbstractMatcher implements Rule {

    @Override
    public @NotNull MatchResult match(@Nullable String content) {
        if (content == null) {
            content = "";
        }
        return match0(content);
    }

    public abstract @NotNull MatchResult match0(@NotNull String content);
}
