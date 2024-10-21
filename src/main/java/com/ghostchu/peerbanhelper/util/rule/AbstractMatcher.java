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
        Main.getServer().getHitRateMetric().addQuery(this);
        if (content == null) {
            content = "";
        }
        MatchResult r = match0(content);
        if (r != MatchResult.DEFAULT) {
            Main.getServer().getHitRateMetric().addHit(this);
        }
        return r;
    }

    public abstract @NotNull MatchResult match0(@NotNull String content);
}
