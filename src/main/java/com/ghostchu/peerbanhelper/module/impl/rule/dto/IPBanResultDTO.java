package com.ghostchu.peerbanhelper.module.impl.rule.dto;

import com.ghostchu.peerbanhelper.util.rule.MatchResult;

public record IPBanResultDTO(String ruleName, MatchResult matchResult) {
}
