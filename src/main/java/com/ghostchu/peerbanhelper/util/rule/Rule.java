package com.ghostchu.peerbanhelper.util.rule;

import com.ghostchu.peerbanhelper.util.JsonUtil;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

public interface Rule {
    @NotNull
    MatchResult match(@NotNull String content);

    Map<String, Object> metadata();

    default String matcherName() {
        return null;
    }

    String matcherIdentifier();

    default String toPrintableText() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("matcherName", matcherName());
        info.put("matcherIdentifier", matcherIdentifier());
        info.put("metadata", metadata());
        return JsonUtil.standard().toJson(info);
    }
}
