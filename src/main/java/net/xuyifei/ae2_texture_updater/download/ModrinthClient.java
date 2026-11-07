package net.xuyifei.ae2_texture_updater.download;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.xuyifei.ae2_texture_updater.utils.HttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;

/**
 * Queries the Modrinth v2 API for the GUI repair resource pack.
 *
 * <p>The target game version is fixed to {@code 1.20.1} (the only Minecraft
 * version this mod supports); a GUI pack must match the running game version,
 * not the texture source version. The latest release whose {@code game_versions}
 * contains 1.20.1 and whose loaders are {@code minecraft} (resource packs have
 * no loader) is selected.</p>
 */
public final class ModrinthClient {
    /** The only Minecraft version this mod supports; GUI packs must match it. */
    public static final String GAME_VERSION = "1.20.1";

    private ModrinthClient() {
    }

    /** A resolved Modrinth file: direct download URL plus its sha1 for integrity checking. */
    public record ModrinthFile(@NotNull String url, @NotNull String filename,
                               @Nullable String sha1, @NotNull String versionNumber) {

        static ModrinthFile of(JsonObject file, String versionNumber) {
            String url = file.get("url").getAsString();
            String filename = file.get("filename").getAsString();
            String sha1 = null;
            if (file.has("hashes") && file.getAsJsonObject("hashes").has("sha1")) {
                sha1 = file.getAsJsonObject("hashes").get("sha1").getAsString();
            }
            return new ModrinthFile(url, filename, sha1, versionNumber);
        }
    }

    /**
     * Finds the newest Modrinth release of {@code projectId} that supports 1.20.1.
     * Returns {@code null} on any failure; the caller decides whether to fall back.
     */
    @Nullable
    public static ModrinthFile fetchLatestGuiPack(String projectId, int timeoutSeconds) {
        if (projectId == null || projectId.isBlank()) {
            return null;
        }
        Request request = new Request.Builder()
                .url("https://api.modrinth.com/v2/project/" + projectId.trim() + "/version")
                .header("User-Agent", UrlBuilder.USER_AGENT)
                .build();

        String body = execute(request, timeoutSeconds);
        if (body == null) {
            return null;
        }
        try {
            JsonArray versions = JsonParser.parseString(body).getAsJsonArray();
            // The API returns versions newest-first; take the first compatible release.
            for (JsonElement element : versions) {
                JsonObject version = element.getAsJsonObject();
                if (!supportsGameVersion(version) || !isResourcePackLoader(version)) {
                    continue;
                }
                ModrinthFile file = pickPrimaryZip(version);
                if (file != null) {
                    return file;
                }
            }
            return null;
        } catch (Exception e) {
            UrlFailureReporter.report("Modrinth GUI pack parse error: " + e.getMessage());
            return null;
        }
    }

    private static boolean supportsGameVersion(JsonObject version) {
        if (!version.has("game_versions")) {
            return false;
        }
        for (JsonElement gv : version.getAsJsonArray("game_versions")) {
            if (GAME_VERSION.equals(gv.getAsString())) {
                return true;
            }
        }
        return false;
    }

    /** Resource packs have loader "minecraft"; reject entries that target specific mod loaders only. */
    private static boolean isResourcePackLoader(JsonObject version) {
        if (!version.has("loaders")) {
            return true; // absent loaders means no filter; accept
        }
        JsonArray loaders = version.getAsJsonArray("loaders");
        if (loaders.isEmpty()) {
            return true;
        }
        for (JsonElement loader : loaders) {
            if ("minecraft".equals(loader.getAsString())) {
                return true;
            }
        }
        return false;
    }

    /** Picks the primary zip file of a version (resource packs ship as .zip, not .jar). */
    @Nullable
    private static ModrinthFile pickPrimaryZip(JsonObject version) {
        if (!version.has("files")) {
            return null;
        }
        String versionNumber = version.has("version_number")
                ? version.get("version_number").getAsString() : "";
        ModrinthFile fallback = null;
        for (JsonElement fileElement : version.getAsJsonArray("files")) {
            JsonObject file = fileElement.getAsJsonObject();
            String filename = file.has("filename")
                    ? file.get("filename").getAsString() : "";
            if (!filename.endsWith(".zip")) {
                continue;
            }
            ModrinthFile candidate = ModrinthFile.of(file, versionNumber);
            if (file.has("primary") && file.get("primary").getAsBoolean()) {
                return candidate;
            }
            if (fallback == null) {
                fallback = candidate;
            }
        }
        return fallback;
    }

    /** Executes a GET and returns the body, or {@code null} on any HTTP/network failure. */
    @Nullable
    private static String execute(@NotNull Request request, int timeoutSeconds) {
        try (Response response = HttpClient.getInstance().newBuilder()
                .callTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .build()
                .newCall(request)
                .execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                UrlFailureReporter.report("Modrinth API HTTP error: " + response.code());
                return null;
            }
            String body = response.body().string();
            return body.isEmpty() ? null : body;
        } catch (Exception e) {
            UrlFailureReporter.report("Modrinth API network error: " + e.getMessage());
            return null;
        }
    }
}
