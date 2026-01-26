package com.ghostchu.peerbanhelper.util.json;

import com.ghostchu.peerbanhelper.util.TimeUtil;
import com.google.gson.*;

import java.lang.reflect.Type;
import java.time.Instant;
import java.time.OffsetDateTime;

public final class OffsetDateTimeTypeAdapter implements JsonSerializer<OffsetDateTime>, JsonDeserializer<OffsetDateTime> {
    public static final OffsetDateTimeTypeAdapter INSTANCE = new OffsetDateTimeTypeAdapter();

    @Override
    public JsonElement serialize(OffsetDateTime ts, Type t, JsonSerializationContext jsc) {
        return new JsonPrimitive(ts.toInstant().toEpochMilli());
    }

    @Override
    public OffsetDateTime deserialize(JsonElement json, Type t, JsonDeserializationContext jsc) throws JsonParseException {
        if (!(json instanceof JsonPrimitive)) {
            throw new JsonParseException("The date should be a number value");
        }
        return Instant.ofEpochMilli(json.getAsLong()).atOffset(TimeUtil.getSystemZoneOffset());
    }
}