package com.ghostchu.peerbanhelper.util;

import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    void preservesExistingYamlWhenSerializationFails() throws IOException {
        Path target = directory.resolve("config.yml");
        Files.writeString(target, "message: old");
        YamlConfiguration configuration = mock(YamlConfiguration.class);
        var failure = new IllegalStateException("serialization failed");
        when(configuration.saveToString()).thenThrow(failure);

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> AtomicFileWriter.saveYaml(configuration, target.toFile())));

        assertEquals("message: old", Files.readString(target));
        assertOnlyFile(target);
    }

    @Test
    void serializesConcurrentYamlSaves() throws Exception {
        Path target = directory.resolve("config.yml");
        CountDownLatch firstSerializing = new CountDownLatch(1);
        CountDownLatch finishFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch secondSerializing = new CountDownLatch(1);
        YamlConfiguration first = mock(YamlConfiguration.class);
        when(first.saveToString()).thenAnswer(invocation -> {
            firstSerializing.countDown();
            assertTrue(finishFirst.await(10, SECONDS));
            return "message: first";
        });
        YamlConfiguration second = mock(YamlConfiguration.class);
        when(second.saveToString()).thenAnswer(invocation -> {
            secondSerializing.countDown();
            return "message: second";
        });

        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstSave = executor.submit(() -> {
                AtomicFileWriter.saveYaml(first, target.toFile());
                return null;
            });
            try {
                assertTrue(firstSerializing.await(10, SECONDS));
                var secondSave = executor.submit(() -> {
                    secondStarted.countDown();
                    AtomicFileWriter.saveYaml(second, target.toFile());
                    return null;
                });
                assertTrue(secondStarted.await(10, SECONDS));
                assertThrows(TimeoutException.class, () -> secondSave.get(200, MILLISECONDS));
                assertEquals(1, secondSerializing.getCount());
                finishFirst.countDown();
                firstSave.get(10, SECONDS);
                secondSave.get(10, SECONDS);
            } finally {
                finishFirst.countDown();
            }
        }

        assertEquals("message: second", Files.readString(target));
    }

    @Test
    void preservesExistingPosixPermissions() throws IOException {
        assumeTrue(Files.getFileStore(directory).supportsFileAttributeView("posix"));
        Path target = directory.resolve("config.yml");
        var permissions = PosixFilePermissions.fromString("rw-rw----");
        Files.createFile(target, PosixFilePermissions.asFileAttribute(permissions));
        Files.setPosixFilePermissions(target, permissions);

        AtomicFileWriter.writeString(target, "message: new");

        assertEquals(permissions, Files.getPosixFilePermissions(target));
        assertEquals("message: new", Files.readString(target));
    }

    private void assertOnlyFile(Path target) throws IOException {
        try (var entries = Files.list(directory)) {
            assertEquals(List.of(target), entries.toList());
        }
    }
}
