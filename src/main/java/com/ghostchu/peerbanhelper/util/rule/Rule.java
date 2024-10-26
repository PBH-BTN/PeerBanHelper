package com.ghostchu.peerbanhelper.util.rule;

import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;

public interface Rule {
    @NotNull
    MatchResult match(@NotNull String content);

    String metadata();

    default TranslationComponent matcherName() {
        return null;
    }

    String matcherIdentifier();

    default String toPrintableText(String locale) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("matcherName", tl(locale, matcherName()));
        info.put("matcherIdentifier", matcherIdentifier());
        info.put("metadata", metadata());
        return JsonUtil.standard().toJson(info);
    }
}
