package com.ghostchu.peerbanhelper.util.rule;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

@EqualsAndHashCode
@ToString
public abstract class AbstractMatcher implements Rule {
    public final MatcherInfo matcherinfo;

    public AbstractMatcher() {
        if (matcherName() != null) {
            matcherinfo = new MatcherInfo(matcherName(), metadata());
        } else {
            matcherinfo = new MatcherInfo(new TranslationComponent(getClass().getName()), metadata());
        }
    }

    @Override
    public @NotNull MatchResult match(@Nullable String content) {
        Main.getServer().getHitRateMetric().addQuery(matcherinfo);
        if (content == null) {
            content = "";
        }
        MatchResult r = match0(content);
        if (r != MatchResult.DEFAULT) {
            Main.getServer().getHitRateMetric().addHit(matcherinfo);
        }
        return r;
    }

    public abstract @NotNull MatchResult match0(@NotNull String content);

    public record MatcherInfo(TranslationComponent ruleType, String metadata) {}
}
