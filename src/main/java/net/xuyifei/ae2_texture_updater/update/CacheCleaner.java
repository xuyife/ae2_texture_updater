package net.xuyifei.ae2_texture_updater.update;

import net.xuyifei.ae2_texture_updater.AE2TextureUpdater;
import net.xuyifei.ae2_texture_updater.download.ModListManager;
import net.xuyifei.ae2_texture_updater.utils.FileUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Removes the per-run working data of the update pipeline.
 *
 * <p>Only <em>dynamic</em> cache is touched: the jars downloaded for this run, what
 * was unpacked from them, the pack being assembled and the pending-mod list. The
 * <em>static</em> cache survives on purpose — the community GUI pack is expensive to
 * re-fetch, the mod-list snapshot is what tells the next launch whether anything
 * changed, and the logs are the player's record of what happened.</p>
 */
public final class CacheCleaner {
    private CacheCleaner() {
    }

    /** Deletes everything produced by an unfinished run; safe to call repeatedly. */
    public static void clearDynamic() {
        UpdatePaths paths = new UpdatePaths();
        FileUtil.cleanDirectory(paths.filesDir());
        FileUtil.cleanDirectory(paths.extractedDir());
        deleteQuietly(paths.packZip());
        deleteQuietly(ModListManager.CACHE_DIR.resolve("to_update_mods.json"));
        AE2TextureUpdater.LOGGER.info("Cancelled update: dynamic cache cleared");
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            AE2TextureUpdater.LOGGER.warn("Unable to delete {} while clearing the cache: {}", path, e.getMessage());
        }
    }
}