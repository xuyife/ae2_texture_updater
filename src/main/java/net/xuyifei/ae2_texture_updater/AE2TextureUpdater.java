package net.xuyifei.ae2_texture_updater;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.StartupMessageManager;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.progress.ProgressMeter;
import net.xuyifei.ae2_texture_updater.config.ModConfig;
import net.xuyifei.ae2_texture_updater.config.entry.AddContentEntry;
import net.xuyifei.ae2_texture_updater.config.entry.DeleteContentEntry;
import net.xuyifei.ae2_texture_updater.config.entry.ModIdMappingEntry;
import net.xuyifei.ae2_texture_updater.config.entry.ModifyContentEntry;
import net.xuyifei.ae2_texture_updater.download.DownloadManager;
import net.xuyifei.ae2_texture_updater.download.ModListManager;
import net.xuyifei.ae2_texture_updater.message.MessageKey;
import net.xuyifei.ae2_texture_updater.message.UserMessageManager;
import net.xuyifei.ae2_texture_updater.utils.JarExtractor;
import net.xuyifei.ae2_texture_updater.utils.FileUtil;
import org.apache.commons.compress.utils.Lists;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Mod(AE2TextureUpdater.MODID)
public class AE2TextureUpdater {
    public static final String MODID = "ae2_texture_updater";
    public static final Logger LOGGER = LoggerFactory.getLogger("AE2TextureUpdater");

    public static ModConfig CONFIG;

    public AE2TextureUpdater(FMLJavaModLoadingContext context) {
        AutoConfig.register(ModConfig.class, GsonConfigSerializer::new);
        CONFIG = AutoConfig.getConfigHolder(ModConfig.class).getConfig();
        context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class, () -> new ConfigScreenHandler.ConfigScreenFactory((mcClient, screen) -> AutoConfig.getConfigScreen(ModConfig.class, screen).get()));

        StartupMessageManager.addModMessage("AE2 Textures Updater");
        UserMessageManager.info(MessageKey.READY);

        if (CONFIG.general.triggerMode != ModConfig.TriggerMode.NEVER) {
            boolean needUpdate;

            ProgressMeter progress1 = StartupMessageManager.addProgressBar("1/5 Scanning Mods", 1);
            UserMessageManager.info(MessageKey.CHECK_MODLIST);

            LOGGER.info("Start Scanning Mods");
            needUpdate = ModListManager.scanMods();
            progress1.complete();
            LOGGER.info("Scan Mods Completed");
            UserMessageManager.info(MessageKey.CHECK_MODLIST_COMPLETE);

            if ((needUpdate && CONFIG.general.triggerMode == ModConfig.TriggerMode.ON_MOD_CHANGE) || CONFIG.general.triggerMode == ModConfig.TriggerMode.ON_STARTUP) {
                UserMessageManager.info(MessageKey.HAS_BEEN_TRIGGERED);
                ProgressMeter progress2 = StartupMessageManager.addProgressBar("2/5 Downloading Mods", 100);
                UserMessageManager.info(MessageKey.START_DOWNLOAD_MODS);

                LOGGER.info("Start Downloading Mods");
                boolean canContinue = DownloadManager.startDownload(progress2);
                LOGGER.info("Download Mods Completed");
                UserMessageManager.info(MessageKey.DOWNLOAD_MODS_COMPLETE);
                progress2.complete();

                Path filesDir = ModListManager.CACHE_DIR.resolve("files");
                AtomicBoolean canContinueOnStep3 = new AtomicBoolean(true);
                if (canContinue) {
                    if (filesDir.toFile().exists()) {
                        ProgressMeter progress3 = StartupMessageManager.addProgressBar("3/5 Unzipping the Jar", 100);
                        UserMessageManager.info(MessageKey.UNZIP_JAR);
                        LOGGER.info("Start Unzipping the Jar");
                        Path extractedDir = filesDir.getParent().resolve("extracted");
                        if (extractedDir.toFile().exists()) {
                            FileUtil.cleanDirectory(extractedDir.toFile());
                        }
                        try (Stream<Path> stream = Files.walk(filesDir)) {
                            stream.forEach(path -> {
                                if (path.toFile().getName().startsWith("download_") && path.toFile().getName().endsWith(".jar")) {
                                    try {
                                        List<String> dirName = JarExtractor.listSubDirectories(path.toFile().getPath(), "assets");
                                        dirName.removeIf(name -> name.equals("minecraft"));
                                        dirName.removeIf(name -> {
                                            if (name.equals("ae2")) {
                                                try {
                                                    List<String> subDirName = JarExtractor.listSubDirectories(path.toFile().getPath(), "assets/ae2");
                                                    return !subDirName.contains("textures") && !subDirName.contains("lang") && !subDirName.contains("models");
                                                } catch (IOException e) {
                                                    LOGGER.error("Error occurred while traversing files, details: {}", e.getMessage());
                                                    UserMessageManager.error(MessageKey.UNKNOWN_ERROR, e.getMessage());
                                                    progress3.complete();
                                                    canContinueOnStep3.set(false);
                                                    return false;
                                                }
                                            }
                                            return false;
                                        });

                                        for (String subDirName : dirName) {
                                            JarExtractor.extractJar(path.toFile().getPath(), "assets/" + subDirName, filesDir.getParent().resolve("extracted").toFile().getPath());
                                        }
                                    } catch (IOException e) {
                                        LOGGER.error("Error occurred while traversing files, details: {}", e.getMessage());
                                        UserMessageManager.error(MessageKey.UNKNOWN_ERROR, e.getMessage());
                                        canContinueOnStep3.set(false);
                                        progress3.complete();
                                    }
                                }
                            });
                        } catch (IOException e) {
                            LOGGER.error("Error occurred while traversing files, details: {}", e.getMessage());
                            UserMessageManager.error(MessageKey.UNKNOWN_ERROR, e.getMessage());
                            canContinueOnStep3.set(false);
                            progress3.complete();
                        }
                        progress3.complete();
                        LOGGER.info("Unzip the Jar Completed");
                        UserMessageManager.info(MessageKey.UNZIP_JAR_COMPLETE);
                    }
                } else {
                    StartupMessageManager.addModMessage("Download Mods failed!");
                    UserMessageManager.error(MessageKey.DOWNLOAD_MODS_FAILED);
                }

                AtomicBoolean canContinueOnStep4 = new AtomicBoolean(true);
                if (canContinueOnStep3.get()) {
                    if (filesDir.getParent().resolve("extracted").toFile().exists()) {
                        File[] files = filesDir.getParent().resolve("extracted").toFile().listFiles();
                        if (files != null) {
                            ProgressMeter progress4 = StartupMessageManager.addProgressBar("4/5 Modifying Mods", 100);
                            UserMessageManager.info(MessageKey.MODIFY_CONTENT);
                            LOGGER.info("Start Modifying Mods");
                            UserMessageManager.info(MessageKey.CLEANUP_CONTENT);
                            LOGGER.info("Cleaning up all mod asset directories");
                            Set<String> needCleanDir = Set.of("ae2guide", "lang", "blockstates");
                            try (Stream<Path> walk = Files.walk(filesDir.getParent().resolve("extracted"), 1)) {
                                walk.filter(Files::isDirectory).filter(path -> !path.equals(filesDir.getParent().resolve("extracted")))
                                        .forEach(modDir -> {
                                            String modName = modDir.getFileName().toString();
                                            LOGGER.info("Cleaning: {}", modName);
                                            UserMessageManager.info(MessageKey.CLEANING, modName);

                                            File[] children = modDir.toFile().listFiles();
                                            if (children == null) return;

                                            for (File child : children) {
                                                String name = child.getName();
                                                try {
                                                    boolean shouldDelete = child.isDirectory() && needCleanDir.contains(name);
                                                    if (shouldDelete) {
                                                        LOGGER.info("Deleted: {}", name);
                                                        UserMessageManager.info(MessageKey.DELETE_FILE, name);
                                                        Files.walk(child.toPath())
                                                                .sorted(Comparator.reverseOrder())
                                                                .forEach(path -> {
                                                                    try {
                                                                        Files.deleteIfExists(path);
                                                                    } catch (IOException e) {
                                                                        LOGGER.error("An error occurred when deleting a file or folder, detail: {}", e.getMessage());
                                                                        UserMessageManager.error(MessageKey.UNKNOWN_ERROR, e.getMessage());
                                                                        progress4.complete();
                                                                        canContinueOnStep4.set(false);
                                                                    }
                                                                });
                                                    }
                                                } catch (IOException e) {
                                                    LOGGER.error("An error occurred when deleting a file or folder, detail: {}", e.getMessage());
                                                    UserMessageManager.error(MessageKey.UNKNOWN_ERROR, e.getMessage());
                                                    progress4.complete();
                                                    canContinueOnStep4.set(false);
                                                }
                                            }
                                        });
                            } catch (IOException e) {
                                LOGGER.error("An error occurred when deleting a file or folder, detail: {}", e.getMessage());
                                UserMessageManager.error(MessageKey.UNKNOWN_ERROR, e.getMessage());
                                progress4.complete();
                                canContinueOnStep4.set(false);
                            }
                            UserMessageManager.info(MessageKey.CLEANUP_CONTENT_COMPLETE);
                            LOGGER.info("Start Add Content");
                            UserMessageManager.info(MessageKey.ADD_CONTENT);
                            StartupMessageManager.addModMessage("Start Add Content");
                            if (!CONFIG.modify.addContentList.entries.isEmpty()) {
                                for (AddContentEntry entry : CONFIG.modify.addContentList.entries) {
                                    if (entry.getPath().startsWith("assets/")) {
                                        String strPath = entry.getPath();
                                        strPath = strPath.replaceFirst("assets/", "");
                                        Path path = ModListManager.CACHE_DIR.resolve("extracted").resolve(strPath);
                                        if (!path.getParent().toFile().exists()) {
                                            path.getParent().toFile().mkdirs();
                                        }
                                        if (path.toFile().isFile()) {
                                            if (!path.toFile().exists()) {
                                                try {
                                                    Files.createFile(path);
                                                    Files.writeString(path, entry.getAddContent());
                                                } catch (IOException e) {
                                                    LOGGER.error("Error creating or writing files, details: {}", e.getMessage());
                                                    UserMessageManager.error(MessageKey.UNKNOWN_ERROR, e.getMessage());
                                                    progress4.complete();
                                                    canContinueOnStep4.set(false);
                                                }
                                            } else {
                                                LOGGER.warn("The file already exists");
                                                UserMessageManager.warning(MessageKey.FILE_ALREADY_EXISTS);
                                            }
                                        } else {
                                            LOGGER.warn("You can only add new files");
                                            UserMessageManager.warning(MessageKey.ADD_ALREADY_EXISTS_FILE_WARN);
                                        }
                                    } else {
                                        LOGGER.warn("You can only add files in the assets directory");
                                        UserMessageManager.warning(MessageKey.ADD_NOT_IN_ASSETS_DIR_FILE_WARN);
                                    }
                                }
                            } else {
                                LOGGER.info("No content to add");
                                UserMessageManager.info(MessageKey.NO_CONTENT_TO_ADD);
                                StartupMessageManager.addModMessage("No content to add");
                            }
                            StartupMessageManager.addModMessage("Add Content Completed");
                            LOGGER.info("Add Content Completed");
                            UserMessageManager.info(MessageKey.ADD_CONTENT_COMPLETE);
                            progress4.setAbsolute(25);

                            LOGGER.info("Start Modify Content");
                            UserMessageManager.info(MessageKey.MODIFY);
                            StartupMessageManager.addModMessage("Start Modify Content");
                            if (!CONFIG.modify.modifyContentList.entries.isEmpty()) {
                                for (ModifyContentEntry entry : CONFIG.modify.modifyContentList.entries) {
                                    if (entry.getPath().startsWith("assets/")) {
                                        String strPath = entry.getPath();
                                        strPath = strPath.replaceFirst("assets/", "");
                                        Path path = ModListManager.CACHE_DIR.resolve("extracted").resolve(strPath);
                                        if (!path.getParent().toFile().exists()) {
                                            path.getParent().toFile().mkdirs();
                                        }
                                        if (path.toFile().isFile()) {
                                            if (path.toFile().exists()) {
                                                try {
                                                    Files.writeString(path, entry.getModifyContent(), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                                                } catch (IOException e) {
                                                    UserMessageManager.error(MessageKey.UNKNOWN_ERROR, e.getMessage());
                                                    LOGGER.error("Error creating or writing files, details: {}", e.getMessage());
                                                    progress4.complete();
                                                    canContinueOnStep4.set(false);
                                                }
                                            } else {
                                                LOGGER.warn("The file does not exist");
                                                UserMessageManager.warning(MessageKey.FILE_NOT_EXISTS);
                                            }
                                        } else {
                                            LOGGER.warn("You can only modify the file");
                                            UserMessageManager.warning(MessageKey.MODIFY_DIR_WARN);
                                        }
                                    } else {
                                        LOGGER.warn("You can only modify files in the assets directory");
                                        UserMessageManager.warning(MessageKey.MODIFY_NOT_IN_ASSETS_DIR_FILE_WARN);
                                    }
                                }
                            } else {
                                LOGGER.info("No content to modify");
                                StartupMessageManager.addModMessage("No content to modify");
                                UserMessageManager.info(MessageKey.NO_CONTENT_TO_MODIFY);
                            }
                            StartupMessageManager.addModMessage("Modify Content Completed");
                            LOGGER.info("Modify Content Completed");
                            UserMessageManager.info(MessageKey.MODIFY_COMPLETE);
                            progress4.setAbsolute(50);

                            LOGGER.info("Start Delete Content");
                            UserMessageManager.info(MessageKey.DELETE_CONTENT);
                            StartupMessageManager.addModMessage("Start Delete Content");
                            if (!CONFIG.modify.deleteContentList.entries.isEmpty()) {
                                for (DeleteContentEntry entry : CONFIG.modify.deleteContentList.entries) {
                                    if (entry.getPath().startsWith("assets/")) {
                                        String strPath = entry.getPath();
                                        strPath = strPath.replaceFirst("assets/", "");
                                        Path path = ModListManager.CACHE_DIR.resolve("extracted").resolve(strPath);
                                        if (!path.getParent().toFile().exists()) {
                                            path.getParent().toFile().mkdirs();
                                        }
                                        if (path.toFile().exists()) {
                                            try {
                                                if (path.toFile().isFile()) {
                                                    Files.delete(path);
                                                } else if (path.toFile().exists()) {
                                                    try (Stream<Path> walk = Files.walk(path)) {
                                                        walk.sorted(Comparator.reverseOrder())
                                                                .forEach(subPath -> {
                                                                    try {
                                                                        Files.delete(subPath);
                                                                    } catch (IOException e) {
                                                                        UserMessageManager.error(MessageKey.UNKNOWN_ERROR, e.getMessage());
                                                                        LOGGER.error("An error occurred when deleting a file or folder, detail: {}", e.getMessage());
                                                                        progress4.complete();
                                                                    }
                                                                });
                                                    }
                                                }
                                            } catch (IOException e) {
                                                UserMessageManager.error(MessageKey.UNKNOWN_ERROR, e.getMessage());
                                                LOGGER.error("An error occurred when deleting a file or folder, detail: {}", e.getMessage());
                                                progress4.complete();
                                                canContinueOnStep4.set(false);
                                            }
                                        } else {
                                            LOGGER.warn("The file does not exist");
                                            UserMessageManager.warning(MessageKey.FILE_NOT_EXISTS);
                                        }
                                    } else {
                                        LOGGER.warn("You can only delete files or directories in the assets directory");
                                        UserMessageManager.warning(MessageKey.DELETE_NOT_IN_ASSETS_DIR_FILE_WARN);
                                    }
                                }
                            } else {
                                LOGGER.info("No content to delete");
                                StartupMessageManager.addModMessage("No content to delete");
                                UserMessageManager.info(MessageKey.NO_CONTENT_TO_DELETE);
                            }
                            StartupMessageManager.addModMessage("Delete Content Completed");
                            UserMessageManager.info(MessageKey.DELETE_CONTENT_COMPLETE);
                            LOGGER.info("Delete Content Completed");
                            progress4.setAbsolute(75);

                            LOGGER.info("Start mapping ModID");
                            UserMessageManager.info(MessageKey.MAPPING_MODID);
                            StartupMessageManager.addModMessage("Start mapping ModID");
                            if (!CONFIG.general.modIdMapping.entries.isEmpty()) {
                                List<String> modIds = Lists.newArrayList();
                                CONFIG.general.modIdMapping.entries.forEach(entry -> modIds.add(entry.getOldModId()));
                                for (String oldModId : modIds) {
                                    if (ModList.get().getMods().stream().filter(iModInfo -> iModInfo.getModId().equals(oldModId)).distinct().findAny().isPresent()) {
                                        String newModId = CONFIG.general.modIdMapping.entries.stream()
                                                .filter(entry -> entry.getOldModId().equals(oldModId))
                                                .findFirst()
                                                .map(ModIdMappingEntry::getNewModId)
                                                .orElseThrow(() -> new IllegalStateException(
                                                        String.format("No ModID mapping found for '%s'. Please configure it in the config file.", oldModId)
                                                ));
                                        LOGGER.info("Find the new ModID for Mod {} and start the replacement", oldModId);
                                        StartupMessageManager.addModMessage(String.format("Find the new ModID for Mod %s and start the replacement", oldModId));
                                        LOGGER.info("Applying ModID mapping: {} -> {}", newModId, oldModId);
                                        UserMessageManager.info(MessageKey.APPLY_MODID, newModId, oldModId);
                                        Path sourcePath = ModListManager.CACHE_DIR.resolve("extracted").resolve(newModId);
                                        if (!sourcePath.toFile().exists()) {
                                            LOGGER.error("No source mod found, possibly the mod download failed");
                                            UserMessageManager.info(MessageKey.UNABLE_TO_FIND_SOURCE_MOD_FILE);
                                            StartupMessageManager.addModMessage("No source mod found, possibly the mod download failed");
                                            progress4.complete();
                                            canContinueOnStep4.set(false);
                                        }
                                        Path targetPath = ModListManager.CACHE_DIR.resolve("extracted").resolve(oldModId);
                                        try {
                                            Files.createDirectories(targetPath.getParent());
                                            Files.deleteIfExists(targetPath);
                                            Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                                            LOGGER.info("Moved directory: {} -> {}", newModId, oldModId);
                                            UserMessageManager.info(MessageKey.MOVE_DIR, newModId, oldModId);

                                            String[] extensions = {".json", ".mcmeta"};

                                            Files.walkFileTree(targetPath, new SimpleFileVisitor<>() {
                                                @Override
                                                public @NotNull FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) {
                                                    String fileName = file.toString();

                                                    boolean shouldProcess = false;
                                                    for (String ext : extensions) {
                                                        if (fileName.endsWith(ext)) {
                                                            shouldProcess = true;
                                                            break;
                                                        }
                                                    }
                                                    if (!shouldProcess) return FileVisitResult.CONTINUE;

                                                    try {
                                                        String content = Files.readString(file);
                                                        String newContent = content;

                                                        newContent = newContent.replaceAll(newModId, oldModId);

                                                        if (!content.equals(newContent)) {
                                                            Files.writeString(file, newContent);
                                                        }
                                                    } catch (IOException e) {
                                                        UserMessageManager.error(MessageKey.UNKNOWN_ERROR, e.getMessage());
                                                        LOGGER.error("Failed to apply ModID: {}", e.getMessage());
                                                        progress4.complete();
                                                        canContinueOnStep4.set(false);
                                                        throw new RuntimeException(e);
                                                    }
                                                    return FileVisitResult.CONTINUE;
                                                }
                                            });
                                        } catch (IOException e) {
                                            UserMessageManager.error(MessageKey.UNKNOWN_ERROR, e.getMessage());
                                            LOGGER.error("Failed to apply ModID: {}", e.getMessage());
                                            progress4.complete();
                                            canContinueOnStep4.set(false);
                                            throw new RuntimeException(e);
                                        }
                                    } else {
                                        LOGGER.info("The {} mod not found, skip!", oldModId);
                                        UserMessageManager.info(MessageKey.MODID_NOT_FOUND);
                                        StartupMessageManager.addModMessage(String.format("The %s mod not found, skip!", oldModId));
                                    }
                                }
                            } else {
                                LOGGER.info("No ModIDs need to be mapped");
                                UserMessageManager.info(MessageKey.NO_MODIDS_TO_MAP);
                                StartupMessageManager.addModMessage("No ModIDs need to be mapped");
                            }
                            LOGGER.info("Map ModID Completed");
                            UserMessageManager.info(MessageKey.MAP_MODID_COMPLETE);
                            StartupMessageManager.addModMessage("Map ModID Completed");

                            LOGGER.info("Modify Mods Completed");
                            UserMessageManager.info(MessageKey.MODIFY_CONTENT_COMPLETE);
                        } else {
                            LOGGER.warn("No extracted mod found");
                            UserMessageManager.warning(MessageKey.NO_EXTRACTED_MOD_FOUND);
                        }
                    } else {
                        LOGGER.warn("Could not find extracted mods directory");
                        UserMessageManager.warning(MessageKey.NO_EXTRACTED_MOD_FOUND);
                    }
                } else {
                    StartupMessageManager.addModMessage("Error unzipping Jar");
                    UserMessageManager.error(MessageKey.ERROR_ON_EXTRACTED_JAR);
                }

                if (canContinueOnStep4.get()) {
                    if (filesDir.getParent().resolve("extracted").toFile().exists()) {
                        File[] files = filesDir.getParent().resolve("extracted").toFile().listFiles();
                        if (files != null) {
                            ProgressMeter progress5 = StartupMessageManager.addProgressBar("5/5 Packaging Files", 100);
                            UserMessageManager.info(MessageKey.PACK_RESOURCEPACK);
                            LOGGER.info("Start Packaging Files");
                            try {
                                Path zipFilePath = filesDir.getParent().resolve("AE2_Texture_Updater.zip");
                                Files.deleteIfExists(zipFilePath);
                                try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFilePath))) {
                                    Path assetsPath = filesDir.getParent().resolve("extracted");

                                    Gson gson = new Gson();
                                    JsonObject root = new JsonObject();
                                    JsonObject pack = new JsonObject();
                                    pack.addProperty("pack_format", 15);
                                    pack.addProperty("description", "This package is automatically generated by AE2 Texture Updater to update AE2's materials\nBy XUYIFEI");
                                    root.add("pack", pack);

                                    ZipEntry mcmetaEntry = new ZipEntry("pack.mcmeta");
                                    mcmetaEntry.setTime(System.currentTimeMillis());
                                    zos.putNextEntry(mcmetaEntry);
                                    zos.write(gson.toJson(root).getBytes(StandardCharsets.UTF_8));
                                    zos.closeEntry();

                                    if (!Files.exists(assetsPath) || !Files.isDirectory(assetsPath)) {
                                        UserMessageManager.warning(MessageKey.NO_EXTRACTED_MOD_FOUND);
                                        LOGGER.warn("extracted directory not found: {}", assetsPath);
                                        return;
                                    }

                                    Files.walkFileTree(assetsPath, new SimpleFileVisitor<>() {
                                        @Override
                                        public @NotNull FileVisitResult preVisitDirectory(@NotNull Path dir, @NotNull BasicFileAttributes attrs)
                                                throws IOException {
                                            if (dir.equals(assetsPath)) {
                                                return FileVisitResult.CONTINUE;
                                            }

                                            Path relativeToAssets = assetsPath.relativize(dir);
                                            String entryName = "assets/" + relativeToAssets.toString().replace('\\', '/') + "/";

                                            ZipEntry entry = new ZipEntry(entryName);
                                            zos.putNextEntry(entry);
                                            zos.closeEntry();

                                            LOGGER.debug("Adding directory: {}", entryName);

                                            return FileVisitResult.CONTINUE;
                                        }

                                        @Override
                                        public @NotNull FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs)
                                                throws IOException {
                                            Path relativeToAssets = assetsPath.relativize(file);
                                            String entryName = "assets/" + relativeToAssets.toString().replace('\\', '/');

                                            ZipEntry entry = new ZipEntry(entryName);
                                            entry.setLastModifiedTime(Files.getLastModifiedTime(file));
                                            zos.putNextEntry(entry);
                                            Files.copy(file, zos);
                                            zos.closeEntry();

                                            LOGGER.debug("Adding file: {}", entryName);

                                            return FileVisitResult.CONTINUE;
                                        }

                                        @Override
                                        public @NotNull FileVisitResult visitFileFailed(@NotNull Path file, @NotNull IOException exc) {
                                            LOGGER.error("Failed to access: {}", file, exc);
                                            UserMessageManager.error(MessageKey.UNKNOWN_ERROR, exc.getMessage());
                                            return FileVisitResult.CONTINUE;
                                        }
                                    });
                                }

                                if (ModListManager.CACHE_DIR.resolve("AE2_Texture_Updater.zip").toFile().exists()) {
                                    Path resPath = FMLPaths.GAMEDIR.get().resolve("resourcepacks");
                                    if (!resPath.toFile().exists()) {
                                        resPath.toFile().mkdir();
                                    }
                                    Files.deleteIfExists(resPath.resolve("AE2_Texture_Updater.zip"));
                                    Files.copy(ModListManager.CACHE_DIR.resolve("AE2_Texture_Updater.zip"), resPath.resolve("AE2_Texture_Updater.zip"), StandardCopyOption.REPLACE_EXISTING);
                                } else {
                                    LOGGER.warn("Unable to package files");
                                    UserMessageManager.error(MessageKey.UNABLE_TO_PACKING_FILES);
                                }
                            } catch (IOException e) {
                                UserMessageManager.error(MessageKey.UNKNOWN_ERROR, e.getMessage());
                                LOGGER.error("Error while packaging files, detail: {}", e.getMessage());
                                progress5.complete();
                            }
                            LOGGER.info("Package Files Completed");
                            UserMessageManager.info(MessageKey.PACK_RESOURCEPACK_COMPLETE);
                            progress5.complete();
                        } else {
                            LOGGER.warn("No extracted mod found");
                            UserMessageManager.warning(MessageKey.NO_EXTRACTED_MOD_FOUND);
                        }
                    } else {
                        StartupMessageManager.addModMessage("Error unzipping Jar");
                        UserMessageManager.error(MessageKey.ERROR_ON_EXTRACTED_JAR);
                    }
                } else {
                    StartupMessageManager.addModMessage("An error occurred while modifying content");
                    UserMessageManager.error(MessageKey.ERROR_ON_MODIFY_CONTENT);
                }
            } else {
                StartupMessageManager.addModMessage("No changes were found in the mod environment or errors occurred during detection");
                UserMessageManager.info(MessageKey.NOT_TRIGGERED);
            }
        } else {
            StartupMessageManager.addModMessage("Since the trigger mode is selected to never trigger, the update will not be triggered this time");
            UserMessageManager.info(MessageKey.NOT_TRIGGERED);
        }
    }
}
