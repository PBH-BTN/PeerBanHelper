package com.ghostchu.peerbanhelper.module.impl.webapi.dto;

import java.util.List;

public record ExperimentRecordDTO(
        String id,
        boolean activated,
        List<Integer> targetGroups,
        String title,
        String description
) {
}
