package net.xuyifei.ae2_texture_updater.download;

import com.google.common.util.concurrent.RateLimiter;
import net.minecraftforge.fml.loading.progress.ProgressMeter;
import net.xuyifei.ae2_texture_updater.AE2TextureUpdater;
import net.xuyifei.ae2_texture_updater.config.ModConfig;
import net.xuyifei.ae2_texture_updater.message.MessageKey;
import net.xuyifei.ae2_texture_updater.message.UserMessageManager;
import net.xuyifei.ae2_texture_updater.utils.FileUtil;
import net.xuyifei.ae2_texture_updater.utils.HttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloads the GUI repair resource pack (from Modrinth or a direct URL) into
 * {@code cache/ae2_texture_updater/gui_pack/} and unpacks it there, applying
 * the shared network settings (threads, speed limit, proxy, timeouts).
 *
 * <p>Unlike the mod downloads, this step reports on the passed-in main progress
 * meter instead of creating its own sub-bar. Download failures fall back to the
 * previously cached zip when one exists, so the pipeline is never blocked by
 * network problems.</p>
 */
public final class GuiPackDownloader {
    private static final Logger LOGGER = LoggerFactory.getLogger("AE2TextureUpdater");
    private static final int BUFFER_SIZE = 8192;

    private final Path zipPath;
    private final Path unpackDir;

    public GuiPackDownloader(Path cacheDir) {
        this.zipPath = cacheDir.resolve("gui_pack").resolve("gui_pack.zip");
        this.unpackDir = cacheDir.resolve("gui_pack").resolve("unpacked");
    }

    public Path unpackDir() {
        return unpackDir;
    }

    public Path zipPath() {
        return zipPath;
    }

    /**
     * Runs the download flow according to {@code guiMerge} config. Returns the
     * directory with the unpacked pack, or {@code null} when the layer is
     * disabled or nothing could be obtained (caller skips the merge step).
     */
    public Path downloadAndExtract(ProgressMeter mainProgress) {
        ModConfig.GuiMerge cfg = AE2TextureUpdater.CONFIG.guiMerge;
        if (!cfg.enabled) {
            UserMessageManager.info(MessageKey.GUI_PACK_SKIPPED_DISABLED);
            return null;
        }

        String url;
        String expectedSha1 = null;
        String versionLabel = "";
        if (cfg.source == ModConfig.GuiMergeSource.MODRINTH) {
            UserMessageManager.info(MessageKey.GUI_PACK_QUERYING_MODRINTH, cfg.modrinthProjectId);
            ModrinthClient.ModrinthFile file = ModrinthClient.fetchLatestGuiPack(
                    cfg.modrinthProjectId, AE2TextureUpdater.CONFIG.download.connect_timeout_seconds);
            if (file == null) {
                return fallbackToCache(mainProgress, null);
            }
            url = file.url();
            expectedSha1 = file.sha1();
            versionLabel = file.versionNumber();
        } else {
            if (cfg.directUrl == null || cfg.directUrl.isBlank()) {
                UserMessageManager.info(MessageKey.GUI_PACK_SKIPPED_NO_URL);
                return null;
            }
            url = cfg.directUrl.trim();
        }

        // Reuse the cached zip when it already matches the expected checksum.
        if (expectedSha1 != null && Files.isRegularFile(zipPath)) {
            String local = sha1Of(zipPath);
            if (expectedSha1.equalsIgnoreCase(local)) {
                LOGGER.info("GUI pack cache hit (version {}), skip download", versionLabel);
                UserMessageManager.info(MessageKey.GUI_PACK_USING_CACHE, versionLabel);
                return extractCached(mainProgress);
            }
        }

        UserMessageManager.info(MessageKey.GUI_PACK_DOWNLOADING, versionLabel.isEmpty() ? url : versionLabel);
        if (!downloadMultiThreaded(url, mainProgress)) {
            return fallbackToCache(mainProgress, expectedSha1);
        }

        if (expectedSha1 != null) {
            String actual = sha1Of(zipPath);
            if (!expectedSha1.equalsIgnoreCase(actual)) {
                LOGGER.warn("GUI pack checksum mismatch: expected {}, actual {}", expectedSha1, actual);
                UserMessageManager.warning(MessageKey.GUI_PACK_CHECKSUM_MISMATCH, expectedSha1, actual);
                return fallbackToCache(mainProgress, expectedSha1);
            }
        }

        UserMessageManager.success(MessageKey.GUI_PACK_DOWNLOAD_COMPLETE);
        return extractCached(mainProgress);
    }

    // ---------------------------------------------------------------- download

    /**
     * Multi-threaded chunked download of the zip, reusing the same positioned-write
     * strategy as {@code DownloadManager}. Progress goes straight onto the main meter.
     */
    @SuppressWarnings("UnstableApiUsage")
    private boolean downloadMultiThreaded(String url, ProgressMeter mainProgress) {
        long fileSize = probeFileSize(url);
        if (fileSize <= 0) {
            return false;
        }
        try {
            Files.createDirectories(zipPath.getParent());
            Files.deleteIfExists(zipPath);
        } catch (IOException e) {
            LOGGER.error("Failed to prepare GUI pack download: {}", e.getMessage());
            UserMessageManager.error(MessageKey.UNKNOWN_ERROR, e.getMessage());
            return false;
        }

        int threadCount = Math.max(1, AE2TextureUpdater.CONFIG.download.threads);
        // A small zip does not benefit from many ranged connections; cap the fan-out.
        threadCount = (int) Math.min(threadCount, Math.max(1, fileSize / (256L * 1024)));

        RateLimiter rateLimiter = AE2TextureUpdater.CONFIG.download.speed_limit_kb > 0
                ? RateLimiter.create(AE2TextureUpdater.CONFIG.download.speed_limit_kb * 1024D)
                : null;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicLong downloadedBytes = new AtomicLong(0L);
        AtomicBoolean failed = new AtomicBoolean(false);

        Thread reporter = new Thread(() -> {
            while (latch.getCount() > 0) {
                if (mainProgress != null) {
                    mainProgress.setAbsolute((int) (downloadedBytes.get() * 100 / fileSize));
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "ae2-texture-updater-gui-progress");
        reporter.setDaemon(true);
        reporter.start();

        long partSize = fileSize / threadCount;
        for (int i = 0; i < threadCount; i++) {
            long startByte = i * partSize;
            long endByte = (i == threadCount - 1) ? fileSize - 1 : (i + 1) * partSize - 1;
            executor.submit(() -> {
                try {
                    downloadChunk(url, startByte, endByte, downloadedBytes, rateLimiter, failed);
                } catch (Exception e) {
                    failed.set(true);
                    LOGGER.error("GUI pack chunk download failed: {}", e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdownNow();
        }

        if (failed.get()) {
            UserMessageManager.error(MessageKey.GUI_PACK_DOWNLOAD_FAILED);
            return false;
        }
        try {
            return Files.size(zipPath) == fileSize;
        } catch (IOException e) {
            return false;
        }
    }

    /** HEAD request returning the content length, or -1 on failure. */
    private long probeFileSize(String url) {
        Request request = new Request.Builder().url(url).head().build();
        try (Response resp = HttpClient.getInstance().newCall(request).execute()) {
            if (!resp.isSuccessful()) {
                LOGGER.error("GUI pack probe failed, HTTP {}: {}", resp.code(), resp.message());
                UserMessageManager.error(MessageKey.GUI_PACK_DOWNLOAD_FAILED);
                return -1;
            }
            String length = resp.header("Content-Length");
            return length == null ? -1 : Long.parseLong(length);
        } catch (Exception e) {
            LOGGER.error("GUI pack probe failed: {}", e.getMessage());
            UserMessageManager.error(MessageKey.GUI_PACK_DOWNLOAD_FAILED);
            return -1;
        }
    }

    private void downloadChunk(String url, long startByte, long endByte,
                               AtomicLong downloadedBytes, RateLimiter rateLimiter, AtomicBoolean failed)
            throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("Range", "bytes=" + startByte + "-" + endByte)
                .build();
        try (Response resp = HttpClient.getInstance().newCall(request).execute()) {
            if (resp.code() != 206 && resp.code() != 200) {
                failed.set(true);
                LOGGER.error("GUI pack chunk HTTP error: {}", resp.code());
                return;
            }
            if (resp.body() == null) {
                failed.set(true);
                return;
            }
            try (InputStream in = resp.body().byteStream()) {
                long pos = startByte;
                byte[] buffer = new byte[BUFFER_SIZE];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    if (rateLimiter != null) {
                        rateLimiter.acquire(len);
                    }
                    writeAt(zipPath, pos, buffer, len);
                    pos += len;
                    downloadedBytes.addAndGet(len);
                }
            }
        }
    }

    /** Writes {@code len} bytes at {@code position} of the target file. */
    private static void writeAt(Path target, long position, byte[] buffer, int len) throws IOException {
        try (FileChannel channel = FileChannel.open(target,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            ByteBuffer wrap = ByteBuffer.wrap(buffer, 0, len);
            while (wrap.hasRemaining()) {
                position += channel.write(wrap, position);
            }
        }
    }

    // ---------------------------------------------------------------- extract

    /** Unpacks the cached zip with zip-slip protection. Returns the unpack dir. */
    private Path extractCached(ProgressMeter mainProgress) {
        UserMessageManager.info(MessageKey.GUI_PACK_EXTRACTING);
        try {
            FileUtil.deleteDirectory(unpackDir);
            Files.createDirectories(unpackDir);
            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    Path resolved = safeResolve(unpackDir, entry.getName());
                    if (resolved == null) {
                        LOGGER.warn("Skipping zip entry outside target dir: {}", entry.getName());
                        continue;
                    }
                    if (entry.isDirectory()) {
                        Files.createDirectories(resolved);
                    } else {
                        Files.createDirectories(resolved.getParent());
                        Files.copy(zis, resolved, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            UserMessageManager.success(MessageKey.GUI_PACK_EXTRACT_COMPLETE);
            return unpackDir;
        } catch (IOException e) {
            LOGGER.error("GUI pack extraction failed: {}", e.getMessage());
            UserMessageManager.error(MessageKey.UNKNOWN_ERROR, e.getMessage());
            return null;
        }
    }

    /** Rejects absolute paths and {@code ..} traversal (zip-slip). */
    private static Path safeResolve(Path base, String entryName) {
        String normalized = entryName.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains("..")) {
            return null;
        }
        Path resolved = base.resolve(normalized);
        return resolved.startsWith(base) ? resolved : null;
    }

    // ---------------------------------------------------------------- fallback

    /** When a fresh download failed, fall back to the previously cached zip if usable. */
    private Path fallbackToCache(ProgressMeter mainProgress, String expectedSha1) {
        if (!Files.isRegularFile(zipPath)) {
            UserMessageManager.error(MessageKey.GUI_PACK_DOWNLOAD_FAILED);
            return null;
        }
        if (expectedSha1 != null) {
            String local = sha1Of(zipPath);
            if (!expectedSha1.equalsIgnoreCase(local)) {
                LOGGER.warn("Cached GUI pack also fails checksum; giving up");
                UserMessageManager.error(MessageKey.GUI_PACK_DOWNLOAD_FAILED);
                return null;
            }
        }
        LOGGER.warn("Falling back to previously cached GUI pack");
        UserMessageManager.warning(MessageKey.GUI_PACK_FALLBACK_CACHE);
        return extractCached(mainProgress);
    }

    /** Lowercase hex sha1 of a file, or empty string when unreadable. */
    private static String sha1Of(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[BUFFER_SIZE];
            int len;
            while ((len = in.read(buffer)) != -1) {
                digest.update(buffer, 0, len);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            LOGGER.error("Failed to hash GUI pack: {}", e.getMessage());
            return "";
        }
    }
}
