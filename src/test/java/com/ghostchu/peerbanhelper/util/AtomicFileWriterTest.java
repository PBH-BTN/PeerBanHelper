package com.ghostchu.peerbanhelper.util;

import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AtomicFileWriterTest {
    @TempDir
    Path directory;

    @Test
    void replacesExistingFileAfterWritingCompletes() throws IOException {
        Path target = directory.resolve("profile.yml");
        Files.writeString(target, "old configuration");
        AtomicFileWriter.replace(target, temporary -> {
            assertEquals(directory, temporary.getParent());
            Files.writeString(temporary, "new configuration");
            assertEquals("old configuration", Files.readString(target));
        });

        assertEquals("new configuration", Files.readString(target));
        assertOnlyFile(target);
    }

    @Test
    void preservesExistingFileWhenWritingFails() throws IOException {
        Path target = directory.resolve("profile.yml");
        Files.writeString(target, "old configuration");
        IOException failure = new IOException("write failed");

        assertSame(failure, assertThrows(IOException.class, () ->
                AtomicFileWriter.replace(target, temporary -> {
                    Files.writeString(temporary, "partial");
                    throw failure;
                })));

        assertEquals("old configuration", Files.readString(target));
        assertOnlyFile(target);
    }

    @Test
    void cleansUpWhenReplacementFails() throws IOException {
        Path target = Files.createDirectory(directory.resolve("occupied"));
        Path existingFile = Files.writeString(target.resolve("existing"), "old");

        assertThrows(IOException.class, () -> AtomicFileWriter.writeString(target, "new"));

        assertEquals("old", Files.readString(existingFile));
        assertOnlyFile(target);
    }

    @Test
    void savesReadableYamlToNewDirectory() throws Exception {
        Path target = directory.resolve("config").resolve("config.yml");
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("message", "配置");

        AtomicFileWriter.saveYaml(configuration, target.toFile());

        YamlConfiguration loaded = new YamlConfiguration();
        loaded.load(target.toFile());
        assertEquals("配置", loaded.getString("message"));
    }

    private void assertOnlyFile(Path target) throws IOException {
        try (var entries = Files.list(directory)) {
            assertEquals(List.of(target), entries.toList());
        }
    }
}
