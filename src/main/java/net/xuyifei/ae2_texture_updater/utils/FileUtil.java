package net.xuyifei.ae2_texture_updater.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Small file helpers built on {@link java.nio.file} APIs.
 */
public final class FileUtil {

    private FileUtil() {
    }

    /** Deletes a directory tree recursively; returns {@code false} when anything could not be deleted. */
    public static boolean deleteDirectory(Path directory) {
        if (directory == null || !Files.isDirectory(directory)) {
            return false;
        }
        try (Stream<Path> walk = Files.walk(directory)) {
            return walk.sorted(Comparator.reverseOrder())
                    .allMatch(path -> {
                        try {
                            return Files.deleteIfExists(path);
                        } catch (IOException e) {
                            return false;
                        }
                    });
        } catch (IOException e) {
            return false;
        }
    }

    /** Removes everything inside {@code directory}, keeping the directory itself. */
    public static boolean cleanDirectory(Path directory) {
        if (directory == null || !Files.isDirectory(directory)) {
            return false;
        }
        try (Stream<Path> entries = Files.list(directory)) {
            boolean allDeleted = true;
            for (Path entry : entries.toList()) {
                if (Files.isDirectory(entry)) {
                    allDeleted &= deleteDirectory(entry);
                } else {
                    try {
                        Files.deleteIfExists(entry);
                    } catch (IOException e) {
                        allDeleted = false;
                    }
                }
            }
            return allDeleted;
        } catch (IOException e) {
            return false;
        }
    }
}
