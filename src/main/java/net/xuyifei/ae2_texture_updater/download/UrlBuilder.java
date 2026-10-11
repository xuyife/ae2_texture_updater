package net.xuyifei.ae2_texture_updater.download;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.xuyifei.ae2_texture_updater.utils.HttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

/**
 * Resolves download URLs from the CurseForge and Modrinth APIs.
 * The fetch methods are synchronous and return "" on any failure;
 * callers handle retries and fallback between platforms.
 */
public final class UrlBuilder {
    public static final String USER_AGENT = "xuyife/AE2TextureUpdater/1.0 (xuyifeiDev@outlook.com)";

    /** Minecraft version the fetched mod files must support. */
    private static final String TARGET_GAME_VERSION = "1.21.1";

    private UrlBuilder() {
    }

    /** Fetches the latest CurseForge file URL; uses the CDN shortcut when no API key is configured. */
    public static String fetchCurseForgeUrl(boolean useApiKey, String apiKey, String projectId,
                                            String fileId, String fileName, int timeoutSeconds) {
        if (!useApiKey) {
            return buildCurseForgeCdnUrl(fileId, fileName);
        }
        if (apiKey == null || apiKey.isEmpty()) {
            return fail("API Key is empty");
        }
        if (projectId == null || projectId.isEmpty()) {
            return fail("Project ID is empty");
        }

        Request request = new Request.Builder()
                .url("https://api.curseforge.com/v1/mods/" + projectId +
                        "/files?gameVersion=" + TARGET_GAME_VERSION + "&modLoaderType=6&index=0&pageSize=10")
                .addHeader("x-api-key", apiKey)
                .build();

        String body = execute(request, timeoutSeconds);
        if (body == null) {
            return "";
        }
        try {
            JsonArray dataArray = JsonParser.parseString(body).getAsJsonObject().getAsJsonArray("data");
            if (dataArray == null || dataArray.isEmpty()) {
                return fail("No mod files found");
            }
            JsonObject latestFile = pickCurseForgeFile(dataArray, fileId);
            return buildCurseForgeCdnUrl(
                    latestFile.get("id").getAsString(),
                    latestFile.get("fileName").getAsString());
        } catch (Exception e) {
            return fail("Parse error: " + e.getMessage());
        }
    }

    /**
     * Picks the configured file when present, otherwise the newest file from the API.
     * Preferring the configured file keeps the CDN id/filename pair consistent.
     */
    private static JsonObject pickCurseForgeFile(JsonArray dataArray, String fileId) {
        if (fileId != null && !fileId.isEmpty()) {
            for (JsonElement element : dataArray) {
                JsonObject file = element.getAsJsonObject();
                if (file.has("id") && String.valueOf(file.get("id").getAsInt()).equals(fileId)) {
                    return file;
                }
            }
        }
        return dataArray.get(0).getAsJsonObject();
    }

    /** Fetches the first Modrinth version file that supports the target game version. */
    public static String fetchModrinthUrl(String projectId, int timeoutSeconds) {
        if (projectId == null || projectId.isEmpty()) {
            return fail("Project ID is empty");
        }

        Request request = new Request.Builder()
                .url("https://api.modrinth.com/v2/project/" + projectId + "/version")
                .header("User-Agent", USER_AGENT)
                .build();

        String body = execute(request, timeoutSeconds);
        if (body == null) {
            return "";
        }
        try {
            JsonArray versions = JsonParser.parseString(body).getAsJsonArray();
            for (JsonElement element : versions) {
                JsonObject version = element.getAsJsonObject();
                if (!supportsGameVersion(version)) {
                    continue;
                }
                String url = firstPrimaryJarUrl(version);
                if (url != null) {
                    return url;
                }
            }
            return fail("No matching version found");
        } catch (Exception e) {
            return fail("Parse error: " + e.getMessage());
        }
    }

    private static boolean supportsGameVersion(JsonObject version) {
        JsonArray gameVersions = version.getAsJsonArray("game_versions");
        if (gameVersions == null) {
            return false;
        }
        for (JsonElement gv : gameVersions) {
            if (gv.getAsString().equals(TARGET_GAME_VERSION)) {
                return true;
            }
        }
        return false;
    }

    /** Prefers the file marked primary, so Modrinth-suggested main files win over any extra jars. */
    private static String firstPrimaryJarUrl(JsonObject version) {
        JsonArray files = version.getAsJsonArray("files");
        if (files == null) {
            return null;
        }
        String fallback = null;
        for (JsonElement fileElement : files) {
            JsonObject file = fileElement.getAsJsonObject();
            if (!file.get("filename").getAsString().endsWith(".jar")) {
                continue;
            }
            String url = file.get("url").getAsString();
            if (file.has("primary") && file.get("primary").getAsBoolean()) {
                return url;
            }
            if (fallback == null) {
                fallback = url;
            }
        }
        return fallback;
    }

    /** Executes a GET and returns the body as a string, mapping HTTP/network errors to "" via {@link #fail}. */
    private static String execute(@NotNull Request request, int timeoutSeconds) {
        try (Response response = HttpClient.getInstance().newBuilder()
                .callTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .build()
                .newCall(request)
                .execute()) {
            int code = response.code();
            if (code == 401 || code == 403) {
                return fail("Invalid API Key");
            }
            if (code == 404) {
                return fail("Project ID not found");
            }
            if (code == 429) {
                return fail("Rate limited, please try again later");
            }
            if (!response.isSuccessful()) {
                return fail("HTTP error: " + code + " - " + response.message());
            }
            if (response.body() == null) {
                return fail("Empty response from API");
            }
            String body = response.body().string();
            return body.isEmpty() ? fail("Empty response from API") : body;
        } catch (Exception e) {
            return fail("Network error: " + e.getMessage());
        }
    }

    private static String fail(String reason) {
        UrlFailureReporter.report(reason);
        return "";
    }

    private static String buildCurseForgeCdnUrl(String fileId, String fileName) {
        if (fileId == null || fileId.isEmpty()) {
            return fail("File ID is empty");
        }
        String prefix = fileId.substring(0, Math.min(4, fileId.length()));
        String suffix = fileId.length() > 4 ? fileId.substring(4, Math.min(7, fileId.length())) : "0";
        return String.format("https://mediafilez.forgecdn.net/files/%s/%s/%s", prefix, suffix, fileName);
    }
}
