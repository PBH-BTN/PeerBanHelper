package com.ghostchu.peerbanhelper.util;

import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public final class AtomicFileWriter {
    private AtomicFileWriter() {
    }

    public static void saveYaml(YamlConfiguration configuration, File target) throws IOException {
        Path path = target.toPath().toAbsolutePath();
        Files.createDirectories(path.getParent());
        replace(path, temporary -> configuration.save(temporary.toFile()));
    }

    public static void write(Path target, byte[] contents) throws IOException {
        replace(target, temporary -> Files.write(temporary, contents));
    }

    public static void writeString(Path target, String contents) throws IOException {
        replace(target, temporary -> Files.writeString(temporary, contents));
    }

    public static void replace(Path target, Writer writer) throws IOException {
        Path destination = target.toAbsolutePath();
        if (Files.isSymbolicLink(destination)) {
            destination = destination.toRealPath();
        }
        Path temporary = Files.createTempFile(destination.getParent(), ".pbh-", ".tmp");
        try {
            writer.write(temporary);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException | Error failure) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException | RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    @FunctionalInterface
    public interface Writer {
        void write(Path temporary) throws IOException;
    }
}
