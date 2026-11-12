package net.xuyifei.ae2_texture_updater.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.fml.ModList;
import net.xuyifei.ae2_texture_updater.AE2TextureUpdater;
import net.xuyifei.ae2_texture_updater.download.UrlBuilder;
import net.xuyifei.ae2_texture_updater.utils.HttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Asks Modrinth whether a newer release of this mod exists.
 *
 * <p>Modrinth is used instead of GitHub because it is reachable from mainland China
 * without a proxy; the shared {@link HttpClient} still honours the configured proxy
 * and timeouts. The query runs on a daemon thread started only after the texture
 * pipeline finished, so it never competes with the downloads for bandwidth.</p>
 */
public final class UpdateChecker {
    /** Page players are sent to; also used as the download target. */
    public static final String PROJECT_URL = "https://modrinth.com/mod/ae2-texture-updater";

    private static final String MOD_ID = AE2TextureUpdater.MODID;
    private static final String PROJECT_ID = "mS3GF6Xu";
    private static final String GAME_VERSION = "1.20.1";
    private static final String LOADER = "forge";

    /** Listeners are notified once a result is available (immediately if it already is). */
    private static final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    private static volatile Result result;
    private static volatile boolean running;

    private UpdateChecker() {
    }

    /** Outcome of one check: the running version, the newest published one, and whether it is newer. */
    public record Result(String currentVersion, String latestVersion, boolean newer) {
    }

    @Nullable
    public static Result result() {
        return result;
    }

    /** Registers a listener that fires as soon as a check result exists. */
    public static void addListener(Runnable listener) {
        listeners.add(listener);
        if (result != null) {
            listener.run();
        }
    }

    /** Starts the check on a daemon thread; a second call while one is running is ignored. */
    public static void checkAsync() {
        if (running) {
            return;
        }
        running = true;
        Thread thread = new Thread(() -> {
            try {
                result = query();
                if (result != null) {
                    AE2TextureUpdater.LOGGER.info("Version check: running {}, latest {}",
                            result.currentVersion(), result.latestVersion());
                }
            } catch (Exception e) {
                AE2TextureUpdater.LOGGER.debug("Version check failed: {}", e.getMessage());
            } finally {
                running = false;
            }
            if (result != null) {
                listeners.forEach(Runnable::run);
            }
        }, "ae2-texture-updater-version-check");
        thread.setDaemon(true);
        thread.start();
    }

    @Nullable
    private static Result query() {
        String current = currentVersion();
        if (current == null) {
            return null;
        }
        String body = fetchVersions();
        if (body == null) {
            return null;
        }
        String latest = parseLatest(body);
        if (latest == null) {
            return null;
        }
        return new Result(current, latest, compareVersions(latest, current) > 0);
    }

    /** The version declared in mods.toml, taken from the loaded mod container. */
    @Nullable
    private static String currentVersion() {
        try {
            return ModList.get().getModContainerById(MOD_ID)
                    .map(container -> container.getModInfo().getVersion().toString())
                    .orElse(null);
        } catch (Exception e) {
            AE2TextureUpdater.LOGGER.debug("Unable to read the running version: {}", e.getMessage());
            return null;
        }
    }

    @Nullable
    private static String fetchVersions() {
        Request request = new Request.Builder()
                .url("https://api.modrinth.com/v2/project/" + PROJECT_ID + "/version")
                .header("User-Agent", UrlBuilder.USER_AGENT)
                .build();
        try (Response response = HttpClient.getInstance().newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return null;
            }
            return response.body().string();
        } catch (IOException e) {
            AE2TextureUpdater.LOGGER.debug("Modrinth version query failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Highest published version that targets 1.20.1 on Forge, ignoring alpha builds.
     * The API order is not relied on: every entry is compared instead.
     */
    @Nullable
    private static String parseLatest(String body) {
        JsonArray versions = JsonParser.parseString(body).getAsJsonArray();
        String latest = null;
        for (JsonElement element : versions) {
            JsonObject version = element.getAsJsonObject();
            if (!contains(version, "game_versions", GAME_VERSION) || !contains(version, "loaders", LOADER)) {
                continue;
            }
            if (version.has("version_type") && "alpha".equals(version.get("version_type").getAsString())) {
                continue;
            }
            if (!version.has("version_number")) {
                continue;
            }
            String number = version.get("version_number").getAsString();
            if (latest == null || compareVersions(number, latest) > 0) {
                latest = number;
            }
        }
        return latest;
    }

    private static boolean contains(JsonObject object, String member, String value) {
        if (!object.has(member) || !object.get(member).isJsonArray()) {
            return false;
        }
        for (JsonElement element : object.getAsJsonArray(member)) {
            if (value.equals(element.getAsString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Lenient version comparison: numeric parts compare numerically, and when the
     * qualifiers differ a missing one is treated as a final release, so
     * {@code 0.4.0 > 0.4.0-beta > 0.4.0-alpha}.
     */
    static int compareVersions(String left, String right) {
        String[] l = left.toLowerCase(Locale.ROOT).split("[.\\-+]");
        String[] r = right.toLowerCase(Locale.ROOT).split("[.\\-+]");
        int parts = Math.max(l.length, r.length);
        for (int i = 0; i < parts; i++) {
            int cmp = comparePart(i < l.length ? l[i] : "", i < r.length ? r[i] : "");
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    private static int comparePart(String left, String right) {
        Integer a = parseNumber(left);
        Integer b = parseNumber(right);
        if (a != null && b != null) {
            return Integer.compare(a, b);
        }
        if (a != null) {
            return 1;
        }
        if (b != null) {
            return -1;
        }
        return Integer.compare(qualifierRank(left), qualifierRank(right));
    }

    @Nullable
    private static Integer parseNumber(String part) {
        if (part.isEmpty()) {
            return null;
        }
        for (int i = 0; i < part.length(); i++) {
            if (!Character.isDigit(part.charAt(i))) {
                return null;
            }
        }
        try {
            return Integer.parseInt(part);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int qualifierRank(String part) {
        return switch (part) {
            case "" -> 100;
            case "snapshot", "dev" -> 10;
            case "alpha" -> 20;
            case "beta" -> 30;
            case "pre", "preview" -> 40;
            case "rc" -> 50;
            case "release", "final" -> 100;
            default -> 60;
        };
    }
}
