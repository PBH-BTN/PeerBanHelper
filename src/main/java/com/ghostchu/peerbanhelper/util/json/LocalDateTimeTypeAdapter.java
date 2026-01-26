package com.ghostchu.peerbanhelper.util.json;

import com.ghostchu.peerbanhelper.util.TimeUtil;
import com.google.gson.*;

import java.lang.reflect.Type;
import java.time.Instant;
import java.time.LocalDateTime;

public final class LocalDateTimeTypeAdapter implements JsonSerializer<LocalDateTime>, JsonDeserializer<LocalDateTime> {
    public static final LocalDateTimeTypeAdapter INSTANCE = new LocalDateTimeTypeAdapter();

    @Override
    public JsonElement serialize(LocalDateTime ts, Type t, JsonSerializationContext jsc) {
        return new JsonPrimitive(ts.toInstant(TimeUtil.getSystemZoneOffset()).toEpochMilli());
    }

    @Override
    public LocalDateTime deserialize(JsonElement json, Type t, JsonDeserializationContext jsc) throws JsonParseException {
        if (!(json instanceof JsonPrimitive)) {
            throw new JsonParseException("The date should be a number value");
        }
        return Instant.ofEpochMilli(json.getAsLong()).atOffset(TimeUtil.getSystemZoneOffset()).toLocalDateTime();
    }
}