package net.xuyifei.ae2_texture_updater.message;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.xuyifei.ae2_texture_updater.download.ModListManager;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Records user-facing update messages to {@code cache/ae2_texture_updater/user_message.json};
 * older files are archived into {@code logs/} as gzip and trimmed to {@link #MAX_LOG_FILES_COUNT}.
 */
public final class UserMessageManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("AE2TextureUpdater-MessageManager");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());

    private static final int MAX_LOG_FILES_COUNT = 15;
    private static final Path MESSAGE_FILE_PATH = ModListManager.CACHE_DIR.resolve("user_message.json");
    private static final Path ARCHIVE_DIR = ModListManager.CACHE_DIR.resolve("logs");
    /** Temporary extraction target for archived logs opened from the log screen. */
    private static final Path TEMP_DIR = ModListManager.CACHE_DIR.resolve("logs_temp");

    private static final List<JsonObject> messageCache = new java.util.ArrayList<>();
    private static boolean initialized = false;

    /**
     * Counts of warning/error messages recorded during this session, so a startup
     * toast can tell the player afterwards that something went wrong. Reset at class
     * load (i.e. per game start), because a new run re-initializes the manager.
     */
    private static int warningCount = 0;
    private static int errorCount = 0;

    private UserMessageManager() {
    }

    public static void info(MessageKey messageKey, Object... args) {
        record(MessageType.INFO, messageKey, args);
    }

    public static void success(MessageKey messageKey, Object... args) {
        record(MessageType.SUCCESS, messageKey, args);
    }

    public static void warning(MessageKey messageKey, Object... args) {
        record(MessageType.WARNING, messageKey, args);
    }

    public static void error(MessageKey messageKey, Object... args) {
        record(MessageType.ERROR, messageKey, args);
    }

    /** Number of WARNING messages recorded during this game session. */
    public static synchronized int warningCount() {
        return warningCount;
    }

    /** Number of ERROR messages recorded during this game session. */
    public static synchronized int errorCount() {
        return errorCount;
    }

    /** True when this session recorded at least one warning or error. */
    public static synchronized boolean hasProblems() {
        return warningCount > 0 || errorCount > 0;
    }

    /** Reads the persisted messages for the log screen; returns an empty array when none exist. */
    public static JsonArray loadMessages() {
        if (!Files.exists(MESSAGE_FILE_PATH)) {
            return new JsonArray();
        }
        try {
            String content = Files.readString(MESSAGE_FILE_PATH);
            if (content == null || content.trim().isEmpty()) {
                return new JsonArray();
            }
            return GSON.fromJson(content, JsonArray.class);
        } catch (IOException e) {
            LOGGER.error("Failed to load messages: {}", e.getMessage());
            return new JsonArray();
        }
    }

    /** Deletes every archived log; exposed for the log screen's clear button. */
    public static void cleanAllOldLogs() {
        try {
            for (Path archive : listArchives()) {
                try {
                    Files.delete(archive);
                } catch (IOException e) {
                    LOGGER.error("Failed to delete archive {}: {}", archive, e.getMessage());
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to clean old logs: {}", e.getMessage());
        }
    }

    // ---------------------------------------------------------------- internals

    private static void record(MessageType messageType, MessageKey messageKey, Object... args) {
        init();

        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("timestamp", FORMATTER.format(Instant.now()));
        jsonObject.addProperty("type", messageType.name().toLowerCase());
        jsonObject.addProperty("key", translationKey(messageKey));

        if (args != null && args.length > 0) {
            JsonArray argsArray = new JsonArray();
            for (Object arg : args) {
                argsArray.add(arg != null ? arg.toString() : "null");
            }
            jsonObject.add("args", argsArray);
        }

        synchronized (messageCache) {
            messageCache.add(jsonObject);
        }
        if (messageType == MessageType.WARNING) {
            synchronized (UserMessageManager.class) {
                warningCount++;
            }
        } else if (messageType == MessageType.ERROR) {
            synchronized (UserMessageManager.class) {
                errorCount++;
            }
        }

        saveToFile();
    }

    private static String translationKey(MessageKey messageKey) {
        return "message.ae2_texture_updater." + messageKey.name().toLowerCase();
    }

    private static synchronized void init() {
        if (initialized) {
            return;
        }
        try {
            Files.createDirectories(ARCHIVE_DIR);
            if (Files.isRegularFile(MESSAGE_FILE_PATH)) {
                String timestamp = FORMATTER.format(Instant.now())
                        .replace(":", "-")
                        .replace(" ", "_");
                Path archivePath = ARCHIVE_DIR.resolve("user_message_" + timestamp + ".json.gz");
                compressFile(MESSAGE_FILE_PATH, archivePath);
                LOGGER.info("Archived old log: {}", archivePath);
                Files.delete(MESSAGE_FILE_PATH);
            }
            trimArchives(MAX_LOG_FILES_COUNT);
            initialized = true;
        } catch (IOException e) {
            LOGGER.error("Failed to initialize log system: {}", e.getMessage());
        }
    }

    private static List<Path> listArchives() throws IOException {
        if (!Files.exists(ARCHIVE_DIR)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(ARCHIVE_DIR)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".gz"))
                    .sorted(Comparator.comparingLong(UserMessageManager::lastModifiedOrMax))
                    .toList();
        }
    }

    private static long lastModifiedOrMax(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return Long.MAX_VALUE;
        }
    }

    /** Keeps only the {@code maxCount} newest archives, deleting the rest (oldest first). */
    private static void trimArchives(int maxCount) throws IOException {
        List<Path> logFiles = listArchives();
        int toDelete = logFiles.size() - maxCount;
        if (toDelete > 0) {
            LOGGER.info("Found {} log files, cleaning {} old ones...", logFiles.size(), toDelete);
            for (int i = 0; i < toDelete; i++) {
                Files.delete(logFiles.get(i));
                LOGGER.info("Deleted old log: {}", logFiles.get(i).getFileName());
            }
        }
    }

    private static void compressFile(Path source, Path target) throws IOException {
        try (InputStream is = new BufferedInputStream(Files.newInputStream(source));
             OutputStream os = new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(target)))) {
            is.transferTo(os);
        }
    }

    // ------------------------------------------------------- archived log access

    /** A single archived log file offered to the log screen's history picker. */
    public record ArchivedLog(@NotNull Path file, @NotNull String displayName, long lastModified) {
    }

    /** Lists archived logs, newest first; the log screen uses this for its picker. */
    public static List<ArchivedLog> listArchivedLogs() {
        List<ArchivedLog> result = new java.util.ArrayList<>();
        try {
            List<Path> files = listArchives();
            for (int i = files.size() - 1; i >= 0; i--) {
                Path file = files.get(i);
                result.add(new ArchivedLog(file, file.getFileName().toString(),
                        lastModifiedOrMax(file) == Long.MAX_VALUE ? 0L : lastModifiedOrMax(file)));
            }
        } catch (IOException e) {
            LOGGER.error("Failed to list archived logs: {}", e.getMessage());
        }
        return result;
    }

    /**
     * Decompresses an archived log into a temporary file and returns the path.
     * Callers must pass it back to {@link #discardTempLog} afterwards so no
     * leftover file remains in the cache directory.
     */
    public static Path extractArchiveToTemp(Path archive) {
        Path temp = null;
        try {
            Files.createDirectories(TEMP_DIR);
            String baseName = archive.getFileName().toString();
            if (baseName.endsWith(".gz")) {
                baseName = baseName.substring(0, baseName.length() - 3);
            }
            temp = TEMP_DIR.resolve(baseName);
            try (InputStream is = new BufferedInputStream(Files.newInputStream(archive));
                 GZIPInputStream gz = new GZIPInputStream(is);
                 OutputStream os = new BufferedOutputStream(Files.newOutputStream(temp))) {
                gz.transferTo(os);
            }
            return temp;
        } catch (IOException e) {
            LOGGER.error("Failed to extract archived log {}: {}", archive, e.getMessage());
            if (temp != null) {
                deleteQuietly(temp);
            }
            return null;
        }
    }

    /** Deletes a temporary log file created by {@link #extractArchiveToTemp}. */
    public static void discardTempLog(Path temp) {
        if (temp != null) {
            deleteQuietly(temp);
        }
    }

    /** Parses a temporary JSON log file into the message array used by the log screen. */
    public static JsonArray loadMessagesFrom(Path jsonFile) {
        if (jsonFile == null || !Files.exists(jsonFile)) {
            return new JsonArray();
        }
        try {
            String content = Files.readString(jsonFile);
            if (content == null || content.trim().isEmpty()) {
                return new JsonArray();
            }
            JsonArray array = GSON.fromJson(content, JsonArray.class);
            return array == null ? new JsonArray() : array;
        } catch (IOException e) {
            LOGGER.error("Failed to read archived log: {}", e.getMessage());
            return new JsonArray();
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            LOGGER.warn("Failed to delete temp log {}: {}", path, e.getMessage());
        }
    }

    private static synchronized void saveToFile() {
        try {
            Files.createDirectories(MESSAGE_FILE_PATH.getParent());
            synchronized (messageCache) {
                Files.writeString(MESSAGE_FILE_PATH, GSON.toJson(messageCache));
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save messages: {}", e.getMessage());
        }
    }
}
