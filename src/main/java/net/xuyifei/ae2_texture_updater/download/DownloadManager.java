package net.xuyifei.ae2_texture_updater.download;

import com.google.common.util.concurrent.RateLimiter;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
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
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class DownloadManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static boolean startDownload(ProgressMeter mainProgress) {
        ModConfig config = AE2TextureUpdater.CONFIG;
        Path modsFilePath = ModListManager.CACHE_DIR.resolve("to_update_mods.json");
        boolean isCurseForgeConnectivity = true;
        boolean isModrinthConnectivity = true;
        if (AE2TextureUpdater.CONFIG.download.check_connectivity) {
            StartupMessageManager.addModMessage("Checking Network");
            UserMessageManager.info(MessageKey.CHECK_NETWORK);
            OkHttpClient client = HttpClient.getInstance();
            Request curseForgeRequest = new Request.Builder()
                    .url("https://api.curseforge.com/")
                    .build();
            try (Response response = client.newCall(curseForgeRequest).execute()) {
                if (response.isSuccessful()) {
                    StartupMessageManager.addModMessage("Successfully connected to the CurseForge API");
                    UserMessageManager.success(MessageKey.SUCCESS_CONNECT_TO_CURSEFORGE);
                } else {
                    LOGGER.warn("Failed to connect to the CurseForge API, code: {}, detail: {}", response.code(), response.message());
                    StartupMessageManager.addModMessage("Failed to connect to the CurseForge API");
                    UserMessageManager.warning(MessageKey.FAIL_CONNECT_TO_CURSEFORGE, response.code(), response.message());

                    isCurseForgeConnectivity = false;
                }
            } catch (IOException e) {
                LOGGER.error("Error on checking network connection, detail: {}", e.getMessage());
                StartupMessageManager.addModMessage("Error on checking network connection");
                UserMessageManager.error(MessageKey.CHECKING_NETWORK_ERR, e.getMessage());
                mainProgress.complete();
                return false;
            }

            Request modrinthRequest = new Request.Builder()
                    .url("https://api.modrinth.com/")
                    .build();

            try (Response response = client.newCall(modrinthRequest).execute()) {
                if (response.isSuccessful()) {
                    StartupMessageManager.addModMessage("Successfully connected to the Modrinth API");
                    UserMessageManager.success(MessageKey.SUCCESS_CONNECT_TO_MODRINTH);
                } else {
                    LOGGER.warn("Failed to connect to the Modrinth API, code: {}, detail: {}", response.code(), response.message());
                    StartupMessageManager.addModMessage("Failed to connect to the Modrinth API");
                    UserMessageManager.warning(MessageKey.FAIL_CONNECT_TO_MODRINTH, response.code(), response.message());
                    isModrinthConnectivity = false;
                }
            } catch (IOException e) {
                LOGGER.error("Error on checking network connection, detail: {}", e.getMessage());
                StartupMessageManager.addModMessage("Error on checking network connection");
                UserMessageManager.error(MessageKey.CHECKING_NETWORK_ERR, e.getMessage());
                mainProgress.complete();
                return false;
            }

            if (!isCurseForgeConnectivity && !isModrinthConnectivity) {
                LOGGER.error("Unable to connect to CurseForge API and Modrinth API, terminate task!");
                StartupMessageManager.addModMessage("Unable to connect to CurseForge API and Modrinth API, terminate task!");
                UserMessageManager.error(MessageKey.FAIL_TO_CONNECT_BOTH);
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                mainProgress.complete();
                return false;
            }
        }

        try {
            JsonArray array = JsonParser.parseString(Files.readString(modsFilePath)).getAsJsonArray();
            List<String> urls = new ArrayList<>();
            if (array != null && !array.isEmpty()) {
                for (int i = 0; i < array.size(); i++) {
                    AtomicReference<String> url = new AtomicReference<>("");
                    AtomicReference<String> curseforgeProjectId = new AtomicReference<>("");
                    AtomicReference<String> fileId = new AtomicReference<>("");
                    AtomicReference<String> fileName = new AtomicReference<>("");
                    AtomicReference<String> modrinthId = new AtomicReference<>("");
                    for (ModEntry entry : config.general.modsToUpdate.entries) {
                        if (entry.getModId().equals(array.get(i).getAsString())) {
                            curseforgeProjectId.set(entry.getCurseForgeProjectId());
                            fileId.set(entry.getCurseForgeFileId());
                            fileName.set(entry.getCurseForgeFileName());
                            modrinthId.set(entry.getModrinthId());
                        }
                    }
                    ProgressMeter progress = StartupMessageManager.prependProgressBar(String.format("Getting %s download URL", array.get(i).toString()), 1);
                    UserMessageManager.info(MessageKey.GETTING_DOWNLOAD_URL, array.get(i).toString());
                    if (config.general.downloadSource.equals(ModConfig.DownloadSource.CURSEFORGE)) {
                        if (isCurseForgeConnectivity) {
                            String downloadURL = getUrlFromCurseForgeApi(config, curseforgeProjectId.get(), fileId.get(), fileName.get(), modrinthId.get(), true);
                            if (downloadURL.isEmpty()) {
                                LOGGER.error("Unable to get the download URL, skip!");
                                UserMessageManager.error(MessageKey.FAIL_TO_GET_URL, array.get(i).toString());
                                progress.complete();
                                continue;
                            } else {
                                url.set(downloadURL);
                                UserMessageManager.success(MessageKey.GET_DOWNLOAD_URL_SUCCESS, array.get(i).toString(), downloadURL);
                                progress.complete();
                            }
                        } else {
                            String downloadURL = getUrlFromModrinthApi(config, curseforgeProjectId.get(), fileId.get(), fileName.get(), modrinthId.get(), true);
                            if (downloadURL.isEmpty()) {
                                LOGGER.error("Unable to get the download URL, skip!");
                                UserMessageManager.error(MessageKey.FAIL_TO_GET_URL, array.get(i).toString());
                                progress.complete();
                                continue;
                            } else {
                                url.set(downloadURL);
                                UserMessageManager.success(MessageKey.GET_DOWNLOAD_URL_SUCCESS, array.get(i).toString(), downloadURL);
                                progress.complete();
                            }
                        }
                    } else if (config.general.downloadSource.equals(ModConfig.DownloadSource.MODRINTH)) {
                        if (isModrinthConnectivity) {
                            String downloadURL = getUrlFromModrinthApi(config, curseforgeProjectId.get(), fileId.get(), fileName.get(), modrinthId.get(), true);
                            if (downloadURL.isEmpty()) {
                                LOGGER.error("Unable to get the download URL, skip!");
                                UserMessageManager.error(MessageKey.FAIL_TO_GET_URL, array.get(i).toString());
                                progress.complete();
                                continue;
                            } else {
                                url.set(downloadURL);
                                UserMessageManager.success(MessageKey.GET_DOWNLOAD_URL_SUCCESS, array.get(i).toString(), downloadURL);
                                progress.complete();
                            }
                        } else {
                            String downloadURL = getUrlFromCurseForgeApi(config, curseforgeProjectId.get(), fileId.get(), fileName.get(), modrinthId.get(), true);
                            if (downloadURL.isEmpty()) {
                                LOGGER.error("Unable to get the download URL, skip!");
                                UserMessageManager.error(MessageKey.FAIL_TO_GET_URL, array.get(i).toString());
                                progress.complete();
                                continue;
                            } else {
                                url.set(downloadURL);
                                UserMessageManager.success(MessageKey.GET_DOWNLOAD_URL_SUCCESS, array.get(i).toString(), downloadURL);
                                progress.complete();
                            }
                        }
                    }
                    urls.add(url.get());
                }
            } else {
                StartupMessageManager.addModMessage("No Mods to update");
                UserMessageManager.info(MessageKey.NO_MODS_TO_UPDATE);
                mainProgress.complete();
                return false;
            }
            mainProgress.setAbsolute(50);

            downloadMods(urls, HttpClient.getInstance());
            mainProgress.complete();
        } catch (IOException e) {
            LOGGER.error("An error occurred during the transfer, details: {}", e.getMessage());
            UserMessageManager.error(MessageKey.UNKNOWN_ERROR, e.getMessage());
        } catch (Exception e) {
            LOGGER.error("An unknown error occurred, details: {}", e.getMessage());
            UserMessageManager.error(MessageKey.UNKNOWN_ERROR, e.getMessage());
        }
        return true;
    }

    private static String getUrlFromCurseForgeApi(@NotNull ModConfig config,
                                                  String curseforgeProjectId,
                                                  String fileId,
                                                  String fileName,
                                                  String modrinthId,
                                                  boolean isFirst) {
        for (int retry = 0; retry < config.download.retry_count; retry++) {
            CompletableFuture<String> future = new CompletableFuture<>();

            UrlBuilder.getCurseForgeUrl(
                    config.general.useApiKey,
                    config.general.curseForgeApiKey,
                    curseforgeProjectId,
                    fileId,
                    fileName,
                    new UrlBuilder.UrlCallback() {
                        @Override
                        public void onSuccess(String downloadUrl) {
                            future.complete(downloadUrl);
                        }

                        @Override
                        public void onError(String errorMessage) {
                            future.completeExceptionally(new Exception(errorMessage));
                        }
                    }
            );

            try {
                String result = future.get(AE2TextureUpdater.CONFIG.download.connect_timeout_seconds, TimeUnit.SECONDS);
                LOGGER.info("Successfully fetched download URL from CurseForge API: {}", result);
                UserMessageManager.success(MessageKey.SUCCESS_FETCHED_DOWNLOAD_URL_FROM_CURSEFORGE, result);
                return result;

            } catch (TimeoutException e) {
                LOGGER.warn("CurseForge API timeout (attempt {}/{}), retrying...", retry + 1, config.download.retry_count);
                UserMessageManager.warning(MessageKey.CURSEFORGE_API_TIMEOUT, retry + 1, config.download.retry_count);
                try {
                    Thread.sleep(config.download.retry_delay_ms);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    LOGGER.warn("Retry interrupted, giving up.");
                    break;
                }
            } catch (Exception e) {
                LOGGER.error("CurseForge API error (attempt {}/{}): {}", retry + 1, config.download.retry_count, e.getMessage());
                UserMessageManager.warning(MessageKey.CURSEFORGE_API_ERR, retry + 1, config.download.retry_count);
                try {
                    Thread.sleep(config.download.retry_delay_ms);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    LOGGER.warn("Retry interrupted, giving up.");
                    break;
                }
            }
        }

        if (isFirst) {
            LOGGER.warn("Unable to get download URL from CurseForge API, falling back to Modrinth...");
            UserMessageManager.warning(MessageKey.CURSEFORGE_GET_FAIL_AND_FALL_BACK_TO_MODRINTH);
            return getUrlFromModrinthApi(config, curseforgeProjectId, fileId, fileName, modrinthId, false);
        }

        return "";
    }

    private static String getUrlFromModrinthApi(@NotNull ModConfig config,
                                                String curseforgeProjectId,
                                                String fileId,
                                                String fileName,
                                                String modrinthId,
                                                boolean isFirst) {
        for (int retry = 0; retry < config.download.retry_count; retry++) {
            CompletableFuture<String> future = new CompletableFuture<>();

            UrlBuilder.getModrinthUrl(
                    modrinthId,
                    new UrlBuilder.UrlCallback() {
                        @Override
                        public void onSuccess(String downloadUrl) {
                            future.complete(downloadUrl);
                        }

                        @Override
                        public void onError(String errorMessage) {
                            future.completeExceptionally(new Exception(errorMessage));
                        }
                    }
            );

            try {
                String result = future.get(AE2TextureUpdater.CONFIG.download.connect_timeout_seconds, TimeUnit.SECONDS);
                LOGGER.info("Successfully fetched download URL from Modrinth API: {}", result);
                UserMessageManager.success(MessageKey.SUCCESS_FETCHED_DOWNLOAD_URL_FROM_MODRINTH, result);
                return result;

            } catch (TimeoutException e) {
                LOGGER.warn("Modrinth API timeout (attempt {}/2), retrying...", retry + 1);
                UserMessageManager.warning(MessageKey.MODRINTH_API_TIMEOUT, retry + 1, config.download.retry_count);
                try {
                    Thread.sleep(config.download.retry_delay_ms);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    LOGGER.warn("Retry interrupted, giving up.");
                    break;
                }
            } catch (Exception e) {
                LOGGER.error("Modrinth API error (attempt {}/2): {}", retry + 1, e.getMessage());
                UserMessageManager.warning(MessageKey.MODRINTH_API_ERR, retry + 1, config.download.retry_count);
                try {
                    Thread.sleep(config.download.retry_delay_ms);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    LOGGER.warn("Retry interrupted, giving up.");
                    break;
                }
            }
        }

        if (isFirst) {
            LOGGER.warn("Unable to get download URL from Modrinth, falling back to CurseForge API...");
            UserMessageManager.warning(MessageKey.MODRINTH_GET_FAIL_AND_FALL_BACK_TO_CURSEFORGE);
            return getUrlFromCurseForgeApi(config, curseforgeProjectId, fileId, fileName, modrinthId, false);
        }

        return "";
    }

    private static void downloadMods(List<String> urls, OkHttpClient client) throws IOException, InterruptedException {
        Path filesDir = ModListManager.CACHE_DIR.resolve("files");
        if (!Files.exists(filesDir)) {
            filesDir.toFile().mkdir();
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(filesDir)) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry)) {
                    Files.delete(entry);
                }
            }
        }
        int treadCount = AE2TextureUpdater.CONFIG.download.threads;
        RateLimiter rateLimiter;
        if (AE2TextureUpdater.CONFIG.download.speed_limit_kb == 0) {
            rateLimiter = null;
        } else {
            rateLimiter = RateLimiter.create(AE2TextureUpdater.CONFIG.download.speed_limit_kb * 1024D);
        }
        for (String url : urls) {
            File toSaveFile = ModListManager.CACHE_DIR.resolve("files").resolve("download_" + System.currentTimeMillis() + ".jar").toFile();
            if (toSaveFile.getParentFile() != null && !toSaveFile.getParentFile().exists()) {
                toSaveFile.getParentFile().mkdirs();
            }
            ProgressMeter progress = StartupMessageManager.prependProgressBar(url, 100);
            long fileSize;
            Request request = new Request.Builder()
                    .url(url)
                    .head()
                    .build();

            try (Response resp = client.newCall(request).execute()) {
                if (resp.code() == 200) {
                    if (resp.header("Content-Length") != null) {
                        fileSize = Long.parseLong(resp.header("Content-Length"));
                    } else {
                        LOGGER.warn("The file is empty!");
                        UserMessageManager.warning(MessageKey.FILE_IS_EMPTY);
                        continue;
                    }
                } else {
                    LOGGER.error("Connection failed, Http Code: {}, details: {}", resp.code(), resp.message());
                    UserMessageManager.error(MessageKey.CONNECT_FAIL, resp.code(), resp.message());
                    continue;
                }
            }

            try (RandomAccessFile raf = new RandomAccessFile(toSaveFile, "rw")) {
                raf.setLength(fileSize);
            }

            FileChannel fileChannel = FileChannel.open(
                    toSaveFile.toPath(),
                    StandardOpenOption.WRITE,
                    StandardOpenOption.READ
            );

            AtomicLong downloadedBytes = new AtomicLong(0L);

            ExecutorService executor = Executors.newFixedThreadPool(treadCount);
            CountDownLatch latch = new CountDownLatch(treadCount);
            long partSize = fileSize / treadCount;

            ScheduledExecutorService progressExecutor =
                    Executors.newSingleThreadScheduledExecutor();
            long finalFileSize = fileSize;
            progressExecutor.scheduleAtFixedRate(() -> {
                long downloaded = downloadedBytes.get();
                int progressCount = (int) (downloaded * 100 / finalFileSize);
                progress.setAbsolute(progressCount);
                if (progressCount == 100) {
                    progress.complete();
                }

                if (latch.getCount() == 0) {
                    progressExecutor.shutdown();
                }
            }, 0, 1, TimeUnit.SECONDS);

            for (int i = 0; i < treadCount; i++) {
                long startByte = i * partSize;
                long endByte = (i == treadCount - 1) ?
                        fileSize - 1 : (i + 1) * partSize - 1;

                int threadId = i;
                executor.submit(() -> {
                    try {
                        Request downloadReq = new Request.Builder()
                                .url(url)
                                .header("Range", "bytes=" + startByte + "-" + endByte)
                                .build();

                        try (Response resp = client.newCall(downloadReq).execute()) {
                            if (resp.code() != 206 && resp.code() != 200) {
                                LOGGER.error("HTTP error, code: {}, message: {}", resp.code(), resp.message());
                                UserMessageManager.error(MessageKey.CONNECT_FAIL, resp.code(), resp.message());
                                return;
                            }

                            InputStream inputStream = resp.body().byteStream();
                            byte[] buffer = new byte[8192];
                            int len;
                            long currentPos = startByte;

                            while ((len = inputStream.read(buffer)) != -1) {
                                RateLimiter limiter = rateLimiter;
                                if (limiter != null) {
                                    limiter.acquire(len);
                                }

                                ByteBuffer byteBuffer = ByteBuffer.wrap(buffer, 0, len);
                                int bytesWritten;
                                do {
                                    bytesWritten = fileChannel.write(byteBuffer, currentPos);
                                    currentPos += bytesWritten;
                                } while (byteBuffer.hasRemaining());

                                downloadedBytes.addAndGet(len);
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.error("Thread {} download failed, details: {}", threadId, e.getMessage());
                        UserMessageManager.error(MessageKey.THREAD_DOWNLOAD_FAIL, threadId, e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            fileChannel.close();
            executor.shutdown();

            File finalFile = new File(toSaveFile.getPath());
            if (finalFile.length() != fileSize) {
                LOGGER.warn("Warning: File size is not as expected, expected size: {}, actual size: {}", fileSize, finalFile.length());
                UserMessageManager.warning(MessageKey.SIZE_IS_NOT_AS_EXPECTED, fileSize, finalFile.length());
            }
        }
    }
}
