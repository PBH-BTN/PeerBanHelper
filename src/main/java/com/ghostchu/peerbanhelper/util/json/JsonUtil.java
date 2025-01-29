package com.ghostchu.peerbanhelper.util.json;

import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.google.gson.*;
import org.jetbrains.annotations.NotNull;

import java.sql.Timestamp;
import java.util.Objects;

public final class JsonUtil {
    private static final Gson DEFAULT_GSON = new Gson();
    private static final Gson STANDARD_GSON = new GsonBuilder()
            .enableComplexMapKeySerialization()
            .setExclusionStrategies(new HiddenAnnotationExclusionStrategy())
            .serializeNulls()
            .registerTypeAdapter(Timestamp.class, TimestampTypeAdapter.INSTANCE)
            .registerTypeAdapter(TranslationComponent.class, TranslationComponentTypeAdapter.INSTANCE)
            .disableHtmlEscaping()
            .create();

    @NotNull
    @Deprecated
    public static Gson get() {
        return standard();
    }

    @NotNull
    public static Gson standard() {
        return STANDARD_GSON;
    }

    public static Gson getGson() {
        return STANDARD_GSON;
    }


    @NotNull
    public static JsonObject readObject(@NotNull String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    public static Gson regular() {
        return STANDARD_GSON;
    }

    @NotNull
    public static String toString(@NotNull JsonElement element) {
        return Objects.requireNonNull(standard().toJson(element));
    }

    public static void writeElement(@NotNull Appendable writer, @NotNull JsonElement element) {
        standard().toJson(element, writer);
    }

    public static void writeObject(@NotNull Appendable writer, @NotNull JsonObject object) {
        standard().toJson(object, writer);
    }

    public @interface Hidden {

    }

    public static class HiddenAnnotationExclusionStrategy implements ExclusionStrategy {
        @Override
        public boolean shouldSkipField(FieldAttributes f) {
            return f.getAnnotation(Hidden.class) != null;
        }

        @Override
        public boolean shouldSkipClass(Class<?> clazz) {
            return clazz.getDeclaredAnnotation(Hidden.class) != null;
        }
    }

}
