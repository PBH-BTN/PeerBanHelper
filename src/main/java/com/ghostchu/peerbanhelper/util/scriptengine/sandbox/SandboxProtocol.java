package com.ghostchu.peerbanhelper.util.scriptengine.sandbox;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.ToNumberPolicy;

import java.io.*;
import java.nio.charset.StandardCharsets;

public final class SandboxProtocol {
    public static final int MAX_FRAME = 1024 * 1024;
    private static final Gson JSON = new GsonBuilder().serializeNulls()
            .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE).create();

    private SandboxProtocol() {}

    public record Init(String extension, String filename, String source, String origin) {}
    public record Metadata(String name, String author, String version, boolean cacheable, boolean threadSafe) {}
    public record Reply(Object result, String error) {}

    public static void write(OutputStream stream, Object value) throws IOException {
        byte[] bytes = JSON.toJson(value).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_FRAME) throw new IOException("Script message too large");
        var output = new DataOutputStream(stream);
        output.writeInt(bytes.length);
        output.write(bytes);
        output.flush();
    }

    public static <T> T read(InputStream stream, Class<T> type) throws IOException {
        var input = new DataInputStream(stream);
        int size = input.readInt();
        int limit = type == Reply.class || type == Metadata.class ? 16384 : MAX_FRAME;
        if (size <= 0 || size > limit) throw new IOException("Invalid script message size");
        byte[] bytes = new byte[size];
        input.readFully(bytes);
        try {
            T value = JSON.fromJson(new String(bytes, StandardCharsets.UTF_8), type);
            if (value == null) throw new IOException("Missing script response");
            if (value instanceof Reply reply && !isScalar(reply.result())) throw new IOException("Invalid script result");
            return value;
        } catch (JsonParseException ex) {
            throw new IOException("Invalid script message", ex);
        }
    }

    static boolean isScalar(Object value) {
        return value == null || value instanceof Boolean || value instanceof String || value instanceof Number;
    }
}
