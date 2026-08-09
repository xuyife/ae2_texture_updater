package net.xuyifei.ae2_texture_updater.message;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.xuyifei.ae2_texture_updater.download.ModListManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

public final class UserMessageManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("AE2TextureUpdater-MessageManager");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private static final int MAX_LOG_FILES_COUNT = 15;
    private static final Path MESSAGE_FILE_PATH = ModListManager.CACHE_DIR.resolve("user_message.json");
    private static final Path ARCHIVE_DIR = ModListManager.CACHE_DIR.resolve("logs");

    private static final List<JsonObject> messageCache = new ArrayList<>();
    private static boolean initialized = false;

    private static synchronized void init() {
        if (initialized) return;

        try {
            if (!Files.exists(ARCHIVE_DIR)) {
                Files.createDirectories(ARCHIVE_DIR);
            }

            if (Files.exists(MESSAGE_FILE_PATH) && Files.isRegularFile(MESSAGE_FILE_PATH)) {
                String timestamp = FORMATTER.format(Instant.now())
                        .replace(":", "-")
                        .replace(" ", "_");
                String archiveName = "user_message_" + timestamp + ".json.gz";
                Path archivePath = ARCHIVE_DIR.resolve(archiveName);

                compressFile(MESSAGE_FILE_PATH, archivePath);
                LOGGER.info("Archived old log: {}", archivePath);
                Files.delete(MESSAGE_FILE_PATH);
            }

            cleanOldLogs();

            initialized = true;
        } catch (IOException e) {
            LOGGER.error("Failed to initialize log system: {}", e.getMessage());
        }
    }

    private static void cleanOldLogs() throws IOException {
        if (!Files.exists(ARCHIVE_DIR)) return;

        try (Stream<Path> stream = Files.list(ARCHIVE_DIR)) {
            java.util.List<Path> logFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".gz"))
                    .sorted(Comparator.comparingLong(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toMillis();
                        } catch (IOException e) {
                            return Long.MAX_VALUE;
                        }
                    }))
                    .toList();

            int toDelete = logFiles.size() - MAX_LOG_FILES_COUNT;
            if (toDelete > 0) {
                LOGGER.info("Found {} log files, cleaning {} old ones...", logFiles.size(), toDelete);
                for (int i = 0; i < toDelete; i++) {
                    Path oldFile = logFiles.get(i);
                    Files.delete(oldFile);
                    LOGGER.info("Deleted old log: {}", oldFile.getFileName());
                }
            }
        }
    }

    private static void compressFile(Path source, Path target) throws IOException {
        try (InputStream is = new BufferedInputStream(Files.newInputStream(source));
             OutputStream os = new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(target)))) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) {
                os.write(buffer, 0, len);
            }
        }
    }

    public static void cleanAllOldLogs() {
        try {
            if (!Files.exists(ARCHIVE_DIR)) return;

            try (Stream<Path> stream = Files.list(ARCHIVE_DIR)) {
                java.util.List<Path> logFiles = stream
                        .filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".gz"))
                        .sorted(Comparator.comparingLong(path -> {
                            try {
                                return Files.getLastModifiedTime(path).toMillis();
                            } catch (IOException e) {
                                return Long.MAX_VALUE;
                            }
                        }))
                        .toList();

                if (!logFiles.isEmpty()) {
                    for (Path logFile : logFiles) {
                        Files.delete(logFile);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to clean old logs: {}", e.getMessage());
        }
    }

    private static void record(MessageType messageType, MessageKey messageKey, Object... args) {
        if (!initialized) {
            init();
        }

        String translationKey = "message.ae2_texture_updater." + messageKey.name().toLowerCase();

        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("timestamp", FORMATTER.format(Instant.now()));
        jsonObject.addProperty("type", messageType.name().toLowerCase());
        jsonObject.addProperty("key", translationKey);

        if (args != null && args.length > 0) {
            JsonArray argsArray = new JsonArray();
            for (Object arg : args) {
                if (arg != null) {
                    argsArray.add(arg.toString());
                } else {
                    argsArray.add("null");
                }
            }
            jsonObject.add("args", argsArray);
        }

        synchronized (messageCache) {
            messageCache.add(jsonObject);
        }

        saveToFile();
    }

    private static synchronized void saveToFile() {
        try {
            if (!Files.exists(MESSAGE_FILE_PATH.getParent())) {
                Files.createDirectories(MESSAGE_FILE_PATH.getParent());
            }
            Files.writeString(MESSAGE_FILE_PATH, GSON.toJson(messageCache));
        } catch (IOException e) {
            LOGGER.error("Failed to save messages: {}", e.getMessage());
        }
    }

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
}
