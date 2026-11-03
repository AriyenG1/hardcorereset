package dev.ariyeng1.hardcorereset;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/** The active run is saved before players receive its persistent per-player run marker. */
public record RunState(UUID id, long seed) {
    public static RunState fresh() {
        return fresh(new SecureRandom().nextLong());
    }

    public static RunState fresh(long seed) {
        return new RunState(UUID.randomUUID(), seed);
    }

    public static RunState readOrCreate(Path path) throws IOException {
        if (!Files.exists(path)) return fresh();
        Properties data = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) {
            data.load(reader);
        }
        if (!data.stringPropertyNames().equals(Set.of("id", "seed"))) {
            throw new IOException("Invalid run state; refusing to silently replace the current world");
        }
        try {
            return new RunState(UUID.fromString(data.getProperty("id")),
                    Long.parseLong(data.getProperty("seed")));
        } catch (IllegalArgumentException error) {
            throw new IOException("Corrupt run state", error);
        }
    }

    public void save(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), "run-", ".tmp");
        Properties data = new Properties();
        data.setProperty("id", id.toString());
        data.setProperty("seed", Long.toString(seed));
        try {
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                data.store(writer, "Hardcore Reset active run; do not edit while the server is running");
            }
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
