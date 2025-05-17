package com.ghostchu.peerbanhelper.util.json;

import com.google.gson.*;

import java.lang.reflect.Type;
import java.sql.Timestamp;

public final class TimestampTypeAdapter implements JsonSerializer<Timestamp>, JsonDeserializer<Timestamp> {
    public static final TimestampTypeAdapter INSTANCE = new TimestampTypeAdapter();

    @Override
    public JsonElement serialize(Timestamp ts, Type t, JsonSerializationContext jsc) {
        return new JsonPrimitive(ts.getTime());
    }

    @Override
    public Timestamp deserialize(JsonElement json, Type t, JsonDeserializationContext jsc) throws JsonParseException {
        if (!(json instanceof JsonPrimitive)) {
            throw new JsonParseException("The date should be a number value");
        }
        return new Timestamp(json.getAsLong());
    }
}