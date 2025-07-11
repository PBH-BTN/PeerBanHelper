package com.ghostchu.peerbanhelper.module.impl.webapi.dto;

import com.google.gson.JsonObject;

public record PushWrapperDTO(String name, String type, JsonObject config) {
}
