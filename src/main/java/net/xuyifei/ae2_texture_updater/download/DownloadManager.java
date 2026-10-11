package net.xuyifei.ae2_texture_updater.download;

import com.google.common.util.concurrent.RateLimiter;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.minecraftforge.fml.StartupMessageManager;
import net.minecraftforge.fml.loading.progress.ProgressMeter;
import net.xuyifei.ae2_texture_updater.AE2TextureUpdater;
import net.xuyifei.ae2_texture_updater.config.ModConfig;
import net.xuyifei.ae2_texture_updater.config.entry.ModEntry;
import net.xuyifei.ae2_texture_updater.message.MessageKey;
import net.xuyifei.ae2_texture_updater.message.UserMessageManager;
import net.xuyifei.ae2_texture_updater.utils.HttpClient;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Step 2/5: resolves download URLs for every mod that needs a texture update
 * and downloads them in parallel with optional speed limiting.
 */
public final class DownloadManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("AE2TextureUpdater");
    private static final String DOWNLOAD_PREFIX = "download_";
    private static final int BUFFER_SIZE = 8192;

    private DownloadManager() {
    }

    public static boolean startDownload(ProgressMeter mainProgress) {
        ModConfig config = AE2TextureUpdater.CONFIG;
        Path modsFilePath = ModListManager.CACHE_DIR.resolve("to_update_mods.json");
        Connectivity connectivity = Connectivity.detect(config, mainProgress);
        if (connectivity == null) {
            return false;
        }

        List<String> urls;
        try {
            urls = resolveUrls(config, modsFilePath, connectivity);
        } catch (IOException e) {
            LOGGER.error("An error occurred during the transfer, details: {}", e.getMessage());
            UserMessageManager.error(MessageKey.UNKNOWN_ERROR, e.getMessage());
            mainProgress.complete();
            return false;
        }
        if (urls == null || urls.isEmpty()) {
            mainProgress.complete();
            return false;
        }

        mainProgress.setAbsolute(50);
        try {
            downloadMods(urls);
        } catch (Exception e) {
            LOGGER.error("An unknown error occurred, details: {}", e.getMessage());
            UserMessageManager.error(MessageKey.UNKNOWN_ERROR, e.getMessage());
        } finally {
            mainProgress.complete();
        }
        return true;
    }

    // ------------------------------------------------------------ connectivity

    /** Probe result for the two mod platforms; {@code null} from {@link #detect} means "abort the whole update". */
    private record Connectivity(boolean curseForge, boolean modrinth) {

        static Connectivity detect(ModConfig config, ProgressMeter mainProgress) {
            if (!config.download.check_connectivity) {
                return new Connectivity(true, true);
            }
            StartupMessageManager.addModMessage("Checking Network");
            UserMessageManager.info(MessageKey.CHECK_NETWORK);

            OkHttpClient client = HttpClient.getInstance();
            boolean cf = probe(client, "https://api.curseforge.com/",
                    MessageKey.SUCCESS_CONNECT_TO_CURSEFORGE, MessageKey.FAIL_CONNECT_TO_CURSEFORGE, mainProgress);
            boolean mr = probe(client, "https://api.modrinth.com/",
                    MessageKey.SUCCESS_CONNECT_TO_MODRINTH, MessageKey.FAIL_CONNECT_TO_MODRINTH, mainProgress);
            if (!cf && !mr) {
                LOGGER.error("Unable to connect to CurseForge API and Modrinth API, terminate task!");
                StartupMessageManager.addModMessage("Unable to connect to CurseForge API and Modrinth API, terminate task!");
                UserMessageManager.error(MessageKey.FAIL_TO_CONNECT_BOTH);
                sleepQuietly(500);
                return null;
            }
            return new Connectivity(cf, mr);
        }

        private static boolean probe(OkHttpClient client, String url,
                                     MessageKey successKey, MessageKey failKey, ProgressMeter mainProgress) {
            Request request = new Request.Builder().url(url).build();
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    StartupMessageManager.addModMessage("Successfully connected to the API");
                    UserMessageManager.success(successKey);
                    return true;
                }
                LOGGER.warn("Failed to connect to the API, code: {}, detail: {}", response.code(), response.message());
                StartupMessageManager.addModMessage("Failed to connect to the API");
                UserMessageManager.warning(failKey, response.code(), response.message());
                return false;
            } catch (IOException e) {
                LOGGER.error("Error on checking network connection, detail: {}", e.getMessage());
                StartupMessageManager.addModMessage("Error on checking network connection");
                UserMessageManager.error(MessageKey.CHECKING_NETWORK_ERR, e.getMessage());
                mainProgress.complete();
                return false;
            }
        }
    }

    // ---------------------------------------------------------------- urls

    /**
     * Reads the mod-id list produced by {@link ModListManager} and resolves one
     * download URL per mod. Returns {@code null} when there is nothing to do.
     */
    private static List<String> resolveUrls(ModConfig config, Path modsFilePath,
                                            Connectivity connectivity) throws IOException {
        JsonArray array = JsonParser.parseString(Files.readString(modsFilePath)).getAsJsonArray();
        if (array == null || array.isEmpty()) {
            StartupMessageManager.addModMessage("No Mods to update");
            UserMessageManager.info(MessageKey.NO_MODS_TO_UPDATE);
            return null;
        }

        List<String> urls = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            String modId = array.get(i).getAsString();
            ModEntry entry = findEntry(config, modId);

            ProgressMeter progress = StartupMessageManager.prependProgressBar("Getting " + modId + " download URL", 1);
            UserMessageManager.info(MessageKey.GETTING_DOWNLOAD_URL, modId);
            String url = resolveSingleUrl(config, entry, connectivity);
            progress.complete();

            if (url.isEmpty()) {
                LOGGER.error("Unable to get the download URL, skip!");
                UserMessageManager.error(MessageKey.FAIL_TO_GET_URL, modId);
            } else {
                UserMessageManager.success(MessageKey.GET_DOWNLOAD_URL_SUCCESS, modId, url);
                urls.add(url);
            }
        }
        return urls;
    }

    private static ModEntry findEntry(ModConfig config, String modId) {
        for (ModEntry entry : config.general.modsToUpdate.entries) {
            if (entry.getModId().equals(modId)) {
                return entry;
            }
        }
        return new ModEntry();
    }

    private static String resolveSingleUrl(ModConfig config, ModEntry entry, Connectivity connectivity) {
        boolean preferCurseForge = config.general.downloadSource == ModConfig.DownloadSource.CURSEFORGE
                ? connectivity.curseForge()
                : !connectivity.modrinth();
        return preferCurseForge
                ? fetchUrlWithFallback(config, entry, true)
                : fetchUrlWithFallback(config, entry, false);
    }

    /** Tries the preferred API first, then falls back to the other one. */
    private static String fetchUrlWithFallback(ModConfig config, ModEntry entry, boolean curseForgeFirst) {
        String url = curseForgeFirst
                ? getUrlFromCurseForgeApi(config, entry, true)
                : getUrlFromModrinthApi(config, entry, true);
        if (!url.isEmpty()) {
            return url;
        }
        return curseForgeFirst
                ? getUrlFromModrinthApi(config, entry, false)
                : getUrlFromCurseForgeApi(config, entry, false);
    }

    private static String getUrlFromCurseForgeApi(ModConfig config, ModEntry entry, boolean isFirst) {
        for (int retry = 0; retry < config.download.retry_count; retry++) {
            String result = UrlBuilder.fetchCurseForgeUrl(
                    config.general.useApiKey,
                    config.general.curseForgeApiKey,
                    entry.getCurseForgeProjectId(),
                    entry.getCurseForgeFileId(),
                    entry.getCurseForgeFileName(),
                    config.download.connect_timeout_seconds);
            if (!result.isEmpty()) {
                LOGGER.info("Successfully fetched download URL from CurseForge API: {}", result);
                UserMessageManager.success(MessageKey.SUCCESS_FETCHED_DOWNLOAD_URL_FROM_CURSEFORGE, result);
                return result;
            }
            LOGGER.warn("CurseForge API failed (attempt {}/{}), retrying...", retry + 1, config.download.retry_count);
            UserMessageManager.warning(MessageKey.CURSEFORGE_API_ERR, retry + 1, config.download.retry_count);
            if (!sleepQuietly(config.download.retry_delay_ms)) {
                break;
            }
        }
        if (isFirst) {
            LOGGER.warn("Unable to get download URL from CurseForge API, falling back to Modrinth...");
            UserMessageManager.warning(MessageKey.CURSEFORGE_GET_FAIL_AND_FALL_BACK_TO_MODRINTH);
            return getUrlFromModrinthApi(config, entry, false);
        }
        return "";
    }

    private static String getUrlFromModrinthApi(ModConfig config, ModEntry entry, boolean isFirst) {
        for (int retry = 0; retry < config.download.retry_count; retry++) {
            String result = UrlBuilder.fetchModrinthUrl(
                    entry.getModrinthId(),
                    config.download.connect_timeout_seconds);
            if (!result.isEmpty()) {
                LOGGER.info("Successfully fetched download URL from Modrinth API: {}", result);
                UserMessageManager.success(MessageKey.SUCCESS_FETCHED_DOWNLOAD_URL_FROM_MODRINTH, result);
                return result;
            }
            LOGGER.warn("Modrinth API failed (attempt {}/{}), retrying...", retry + 1, config.download.retry_count);
            UserMessageManager.warning(MessageKey.MODRINTH_API_ERR, retry + 1, config.download.retry_count);
            if (!sleepQuietly(config.download.retry_delay_ms)) {
                break;
            }
        }
        if (isFirst) {
            LOGGER.warn("Unable to get download URL from Modrinth, falling back to CurseForge API...");
            UserMessageManager.warning(MessageKey.MODRINTH_GET_FAIL_AND_FALL_BACK_TO_CURSEFORGE);
            return getUrlFromCurseForgeApi(config, entry, false);
        }
        return "";
    }

    // -------------------------------------------------------------- download

    private static void downloadMods(List<String> urls) throws IOException, InterruptedException {
        Path filesDir = ModListManager.CACHE_DIR.resolve("files");
        Files.createDirectories(filesDir);
        clearDirectory(filesDir);

        int threadCount = Math.max(1, AE2TextureUpdater.CONFIG.download.threads);
        RateLimiter rateLimiter = createRateLimiter();

        for (String url : urls) {
            Path target = filesDir.resolve(DOWNLOAD_PREFIX + System.currentTimeMillis() + ".jar");
            if (!downloadSingle(url, target, threadCount, rateLimiter)) {
                Files.deleteIfExists(target);
            }
        }
    }

    private static RateLimiter createRateLimiter() {
        int speedLimitKb = AE2TextureUpdater.CONFIG.download.speed_limit_kb;
        return speedLimitKb > 0 ? RateLimiter.create(speedLimitKb * 1024D) : null;
    }

    private static void clearDirectory(Path dir) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry)) {
                    Files.delete(entry);
                }
            }
        }
    }

    /**
     * Downloads one file with a HEAD request to learn its size, then a ranged
     * GET per thread chunk. Returns {@code false} when the file was not fully
     * downloaded (caller deletes the partial file).
     */
    private static boolean downloadSingle(String url, Path target, int threadCount, RateLimiter rateLimiter)
            throws IOException, InterruptedException {
        ProgressMeter progress = StartupMessageManager.prependProgressBar(url, 100);

        long fileSize = probeFileSize(url);
        if (fileSize < 0) {
            progress.complete();
            return false;
        }

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicLong downloadedBytes = new AtomicLong(0L);
        AtomicBoolean failed = new AtomicBoolean(false);

        startProgressReporter(progress, latch, downloadedBytes, fileSize);

        long partSize = fileSize / threadCount;
        for (int i = 0; i < threadCount; i++) {
            long startByte = i * partSize;
            long endByte = (i == threadCount - 1) ? fileSize - 1 : (i + 1) * partSize - 1;
            int threadId = i;
            executor.submit(() -> {
                try {
                    downloadChunk(url, startByte, endByte, target, downloadedBytes, rateLimiter, failed);
                } catch (Exception e) {
                    failed.set(true);
                    LOGGER.error("Thread {} download failed, details: {}", threadId, e.getMessage());
                    UserMessageManager.error(MessageKey.THREAD_DOWNLOAD_FAIL, threadId, e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
        } finally {
            executor.shutdownNow();
        }

        boolean ok = !failed.get() && isComplete(target, fileSize);
        if (ok) {
            progress.complete();
        }
        return ok;
    }

    /** HEAD request returning the content length, or -1 if the size is unknown. */
    private static long probeFileSize(String url) throws IOException {
        Request request = new Request.Builder().url(url).head().build();
        try (Response resp = HttpClient.getInstance().newCall(request).execute()) {
            if (!resp.isSuccessful()) {
                LOGGER.error("Connection failed, Http Code: {}, details: {}", resp.code(), resp.message());
                UserMessageManager.error(MessageKey.CONNECT_FAIL, resp.code(), resp.message());
                return -1;
            }
            String length = resp.header("Content-Length");
            if (length == null) {
                LOGGER.warn("The file is empty!");
                UserMessageManager.warning(MessageKey.FILE_IS_EMPTY);
                return -1;
            }
            return Long.parseLong(length);
        }
    }

    private static void startProgressReporter(ProgressMeter progress, CountDownLatch latch,
                                              AtomicLong downloadedBytes, long fileSize) {
        Thread reporter = new Thread(() -> {
            while (latch.getCount() > 0) {
                long downloaded = downloadedBytes.get();
                progress.setAbsolute((int) (downloaded * 100 / fileSize));
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "ae2-texture-updater-progress");
        reporter.setDaemon(true);
        reporter.start();
    }

    private static void downloadChunk(String url, long startByte, long endByte, Path target,
                                      AtomicLong downloadedBytes, RateLimiter rateLimiter, AtomicBoolean failed)
            throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("Range", "bytes=" + startByte + "-" + endByte)
                .build();

        try (Response resp = HttpClient.getInstance().newCall(request).execute()) {
            if (resp.code() != 206 && resp.code() != 200) {
                LOGGER.error("HTTP error, code: {}, message: {}", resp.code(), resp.message());
                UserMessageManager.error(MessageKey.HTTP_ERROR, resp.code(), resp.message());
                failed.set(true);
                return;
            }
            if (resp.body() == null) {
                LOGGER.error("Empty download body, thread chunk {}-{}", startByte, endByte);
                UserMessageManager.error(MessageKey.HTTP_ERROR, resp.code(), "empty body");
                failed.set(true);
                return;
            }

            try (InputStream inputStream = resp.body().byteStream()) {
                long currentPos = startByte;
                byte[] buffer = new byte[BUFFER_SIZE];
                int len;
                while ((len = inputStream.read(buffer)) != -1) {
                    if (rateLimiter != null) {
                        rateLimiter.acquire(len);
                    }
                    writeChunkAt(target, currentPos, buffer, len);
                    currentPos += len;
                    downloadedBytes.addAndGet(len);
                }
            }
        }
    }

    /** Writes {@code len} bytes at {@code position} without holding the channel open between chunks. */
    private static void writeChunkAt(Path target, long position, byte[] buffer, int len) throws IOException {
        try (FileChannel channel = FileChannel.open(target,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            ByteBuffer wrap = ByteBuffer.wrap(buffer, 0, len);
            while (wrap.hasRemaining()) {
                position += channel.write(wrap, position);
            }
        }
    }

    private static boolean isComplete(Path target, long fileSize) {
        long actual;
        try {
            actual = Files.exists(target) ? Files.size(target) : 0;
        } catch (IOException e) {
            actual = -1;
        }
        if (actual != fileSize) {
            LOGGER.warn("Warning: File size is not as expected, expected size: {}, actual size: {}", fileSize, actual);
            UserMessageManager.warning(MessageKey.SIZE_IS_NOT_AS_EXPECTED, fileSize, actual);
            return false;
        }
        return true;
    }

    // --------------------------------------------------------------- helpers

    private static boolean sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
