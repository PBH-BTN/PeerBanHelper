package com.ghostchu.peerbanhelper.module.impl.webapi.dto;

import java.util.List;

public record ReplaceTrackerDTO(String from, String to, List<String> downloaders) {

}
