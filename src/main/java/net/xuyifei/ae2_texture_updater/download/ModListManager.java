package net.xuyifei.ae2_texture_updater.download;

import com.google.gson.JsonArray;
import com.mojang.logging.LogUtils;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.xuyifei.ae2_texture_updater.AE2TextureUpdater;
import net.xuyifei.ae2_texture_updater.config.ModConfig;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModListManager {
    public static final Path CACHE_DIR = FMLPaths.GAMEDIR.get().resolve("cache").resolve("ae2_texture_updater");
    private static final Logger LOGGER = LogUtils.getLogger();

    public static boolean scanMods() {
        ModList modList = ModList.get();
        JsonArray rootArray = new JsonArray();
        modList.getMods().forEach(mod -> {
            String modId = mod.getModId();
            rootArray.add(modId);
        });
        if (AE2TextureUpdater.CONFIG.general.debugMode) {
            LOGGER.debug("List of mods obtained: {}", rootArray);
        }
        Path snapshotPath = CACHE_DIR.resolve("modlist_snapshot.json");
        if (Files.notExists(CACHE_DIR)) {
            LOGGER.info("Cache folder does not exist, Create!");
            try {
                Files.createDirectories(CACHE_DIR);
            } catch (IOException e) {
                LOGGER.error("Failed to create cache folder, details: {}", e.getMessage());
                throw new RuntimeException(e);
            }
        }
        if (Files.exists(snapshotPath)) {
            try {
                if (!Files.readString(snapshotPath).equals(rootArray.toString())) {
                    Files.writeString(snapshotPath, rootArray.toString());
                    checkMods();
                    return true;
                }
            } catch (IOException e) {
                LOGGER.error("Failed to create mods list file, details: {}", e.getMessage());
                throw new RuntimeException(e);
            }
        } else {
            try {
                Files.createFile(snapshotPath);
                Files.writeString(snapshotPath, rootArray.toString());
                checkMods();
                return true;
            } catch (IOException e) {
                LOGGER.error("Failed to create mods list file, details: {}", e.getMessage());
                throw new RuntimeException(e);
            }
        }
        return false;
    }

    private static void checkMods() throws IOException {
        LOGGER.info("Mod environment changes are detected, re-scan mods that need to update textures.");
        ModList modList = ModList.get();
        ModConfig config = AutoConfig.getConfigHolder(ModConfig.class).getConfig();
        JsonArray rootArray = new JsonArray();
        modList.getMods().forEach(mod -> config.general.modsToUpdate.entries.forEach(entry -> {
            if (entry.getModId().equals(mod.getModId())) {
                String modId = mod.getModId();
                rootArray.add(modId);
            }
        }));
        if (AE2TextureUpdater.CONFIG.general.debugMode) {
            LOGGER.debug("List of mods that need to update textures obtained: {}", rootArray);
        }
        Path toUpdateModsPath = CACHE_DIR.resolve("to_update_mods.json");
        if (Files.exists(toUpdateModsPath)) {
            Files.delete(toUpdateModsPath);
        }
        Files.createFile(toUpdateModsPath);
        Files.writeString(toUpdateModsPath, rootArray.toString());
    }
}
