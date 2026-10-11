package net.xuyifei.ae2_texture_updater.download;

import com.google.gson.JsonArray;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.xuyifei.ae2_texture_updater.AE2TextureUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Step 1/5: snapshots the installed mod ids and writes the list of mods that
 * need texture updates to {@code to_update_mods.json} for {@link DownloadManager}.
 */
public final class ModListManager {
    public static final Path CACHE_DIR = FMLPaths.GAMEDIR.get()
            .resolve("cache").resolve("ae2_texture_updater");
    private static final Logger LOGGER = LoggerFactory.getLogger("AE2TextureUpdater");

    private ModListManager() {
    }

    /**
     * Compares the current mod list with the cached snapshot.
     * Returns {@code true} when the mod environment changed (or no snapshot exists yet).
     */
    public static boolean scanMods() {
        String currentModIds = installedModIds().toString();
        if (AE2TextureUpdater.CONFIG.general.debugMode) {
            LOGGER.debug("List of mods obtained: {}", currentModIds);
        }

        Path snapshotPath = CACHE_DIR.resolve("modlist_snapshot.json");
        try {
            Files.createDirectories(CACHE_DIR);
            String previous = Files.exists(snapshotPath) ? Files.readString(snapshotPath) : null;
            boolean changed = previous == null || !previous.equals(currentModIds);
            if (changed) {
                Files.writeString(snapshotPath, currentModIds);
                writeToUpdateList();
            }
            return changed;
        } catch (IOException e) {
            LOGGER.error("Failed to update mods list file, details: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private static JsonArray installedModIds() {
        JsonArray array = new JsonArray();
        ModList.get().getMods().forEach(mod -> array.add(mod.getModId()));
        return array;
    }

    /** Writes the ids of configured mods that are currently installed. */
    private static void writeToUpdateList() throws IOException {
        LOGGER.info("Mod environment changes are detected, re-scan mods that need to update textures.");
        JsonArray needed = new JsonArray();
        for (var entry : AE2TextureUpdater.CONFIG.general.modsToUpdate.entries) {
            String modId = entry.getModId();
            boolean installed = ModList.get().getMods().stream().anyMatch(mod -> mod.getModId().equals(modId));
            if (installed && !containsId(needed, modId)) {
                needed.add(modId);
            }
        }
        if (AE2TextureUpdater.CONFIG.general.debugMode) {
            LOGGER.debug("List of mods that need to update textures obtained: {}", needed);
        }
        Files.writeString(CACHE_DIR.resolve("to_update_mods.json"), needed.toString());
    }

    private static boolean containsId(JsonArray array, String modId) {
        for (int i = 0; i < array.size(); i++) {
            if (array.get(i).getAsString().equals(modId)) {
                return true;
            }
        }
        return false;
    }
}
