package net.xuyifei.ae2_texture_updater.update;

import net.minecraftforge.fml.StartupMessageManager;
import net.minecraftforge.fml.loading.progress.ProgressMeter;
import net.xuyifei.ae2_texture_updater.AE2TextureUpdater;
import net.xuyifei.ae2_texture_updater.config.entry.AddContentEntry;
import net.xuyifei.ae2_texture_updater.config.entry.DeleteContentEntry;
import net.xuyifei.ae2_texture_updater.config.entry.ModIdMappingEntry;
import net.xuyifei.ae2_texture_updater.config.entry.ModifyContentEntry;
import net.xuyifei.ae2_texture_updater.message.MessageKey;
import net.xuyifei.ae2_texture_updater.message.UserMessageManager;
import net.xuyifei.ae2_texture_updater.utils.FileUtil;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.FileVisitResult;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Step 4/5: applies the configured content operations to the extracted trees:
 * cleanup of vanilla-synced dirs, add / modify / delete entries, then mod-id mapping.
 */
public final class ModificationStep {
    private static final Logger LOGGER = LoggerFactory.getLogger("AE2TextureUpdater");

    /** Asset dirs that would be overwritten by the real mods at runtime and must be cleaned out of the pack. */
    private static final Set<String> CLEANUP_DIRS = Set.of("ae2guide", "lang", "blockstates");
    private static final String ASSETS_PREFIX = "assets/";

    private final UpdatePaths paths;

    public ModificationStep(UpdatePaths paths) {
        this.paths = paths;
    }

    public boolean run() {
        if (!Files.isDirectory(paths.extractedDir())) {
            UserMessageManager.warning(MessageKey.NO_EXTRACTED_MOD_FOUND);
            LOGGER.warn("Could not find extracted mods directory");
            return true;
        }
        try (Stream<Path> entries = Files.list(paths.extractedDir())) {
            if (entries.findAny().isEmpty()) {
                UserMessageManager.warning(MessageKey.NO_EXTRACTED_MOD_FOUND);
                LOGGER.warn("No extracted mod found");
                return true;
            }
        } catch (IOException e) {
            LOGGER.error("Failed to list extracted directory: {}", e.getMessage());
            UserMessageManager.error(MessageKey.UNKNOWN_ERROR, e.getMessage());
            return false;
        }

        ProgressMeter progress = StartupMessageManager.addProgressBar("4/5 Modifying Mods", 100);
        UpdateProgress percent = new UpdateProgress(progress, progress.steps() == 0 ? 1 : progress.steps());
        UserMessageManager.info(MessageKey.MODIFY_CONTENT);
        LOGGER.info("Start Modifying Mods");

        boolean ok = cleanupDirs(percent)
                && addContent(percent)
                && modifyContent(percent)
                && deleteContent(percent)
                && mapModIds(percent);

        if (ok) {
            LOGGER.info("Modify Mods Completed");
            UserMessageManager.info(MessageKey.MODIFY_CONTENT_COMPLETE);
        }
        progress.complete();
        return ok;
    }

    // ---------------------------------------------------------------- cleanup

    private boolean cleanupDirs(UpdateProgress progress) {
        UserMessageManager.info(MessageKey.CLEANUP_CONTENT);
        LOGGER.info("Cleaning up all mod asset directories");
        try (Stream<Path> modDirs = Files.list(paths.extractedDir())) {
            for (Path modDir : modDirs.filter(Files::isDirectory).toList()) {
                String modName = modDir.getFileName().toString();
                LOGGER.info("Cleaning: {}", modName);
                UserMessageManager.info(MessageKey.CLEANING, modName);
                if (!deleteNamedChildren(modDir, progress)) {
                    return false;
                }
            }
        } catch (IOException e) {
            LOGGER.error("An error occurred when deleting a file or folder, detail: {}", e.getMessage());
            UserMessageManager.error(MessageKey.UNKNOWN_ERROR, e.getMessage());
            return false;
        }
        UserMessageManager.info(MessageKey.CLEANUP_CONTENT_COMPLETE);
        return true;
    }

    private boolean deleteNamedChildren(Path modDir, UpdateProgress progress) {
        File[] children = modDir.toFile().listFiles();
        if (children == null) {
            return true;
        }
        for (File child : children) {
            if (child.isDirectory() && CLEANUP_DIRS.contains(child.getName())) {
                LOGGER.info("Deleted: {}", child.getName());
                UserMessageManager.info(MessageKey.DELETE_FILE, child.getName());
                if (!deleteRecursively(child.toPath(), progress)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean deleteRecursively(Path root, UpdateProgress progress) {
        return FileUtil.deleteDirectory(root);
    }

    // ------------------------------------------------------------------- add

    private boolean addContent(UpdateProgress progress) {
        LOGGER.info("Start Add Content");
        UserMessageManager.info(MessageKey.ADD_CONTENT);
        StartupMessageManager.addModMessage("Start Add Content");

        List<AddContentEntry> entries = AE2TextureUpdater.CONFIG.modify.addContentList.entries;
        if (entries.isEmpty()) {
            LOGGER.info("No content to add");
            UserMessageManager.info(MessageKey.NO_CONTENT_TO_ADD);
        } else {
            for (AddContentEntry entry : entries) {
                if (!requireAssetsPath(entry.getPath(), MessageKey.ADD_NOT_IN_ASSETS_DIR_FILE_WARN)) {
                    continue;
                }
                Path path = paths.resolvedInExtracted(stripAssetsPrefix(entry.getPath()));
                if (isDirectory(path)) {
                    UserMessageManager.warning(MessageKey.ADD_ALREADY_EXISTS_FILE_WARN);
                    LOGGER.warn("You can only add new files");
                    continue;
                }
                if (Files.exists(path)) {
                    LOGGER.warn("The file already exists");
                    UserMessageManager.warning(MessageKey.FILE_ALREADY_EXISTS);
                    continue;
                }
                if (!writeFile(path, entry.getAddContent(), false, progress)) {
                    return false;
                }
            }
        }
        StartupMessageManager.addModMessage("Add Content Completed");
        LOGGER.info("Add Content Completed");
        UserMessageManager.info(MessageKey.ADD_CONTENT_COMPLETE);
        progress.setPercent(25);
        return true;
    }

    // ---------------------------------------------------------------- modify

    private boolean modifyContent(UpdateProgress progress) {
        LOGGER.info("Start Modify Content");
        UserMessageManager.info(MessageKey.MODIFY);
        StartupMessageManager.addModMessage("Start Modify Content");

        List<ModifyContentEntry> entries = AE2TextureUpdater.CONFIG.modify.modifyContentList.entries;
        if (entries.isEmpty()) {
            LOGGER.info("No content to modify");
            StartupMessageManager.addModMessage("No content to modify");
            UserMessageManager.info(MessageKey.NO_CONTENT_TO_MODIFY);
        } else {
            for (ModifyContentEntry entry : entries) {
                if (!requireAssetsPath(entry.getPath(), MessageKey.MODIFY_NOT_IN_ASSETS_DIR_FILE_WARN)) {
                    continue;
                }
                Path path = paths.resolvedInExtracted(stripAssetsPrefix(entry.getPath()));
                if (!isRegularFile(path)) {
                    LOGGER.warn("You can only modify the file");
                    UserMessageManager.warning(MessageKey.MODIFY_DIR_WARN);
                    continue;
                }
                if (!Files.exists(path)) {
                    LOGGER.warn("The file does not exist");
                    UserMessageManager.warning(MessageKey.FILE_NOT_EXISTS);
                    continue;
                }
                if (!writeFile(path, entry.getModifyContent(), true, progress)) {
                    return false;
                }
            }
        }
        StartupMessageManager.addModMessage("Modify Content Completed");
        LOGGER.info("Modify Content Completed");
        UserMessageManager.info(MessageKey.MODIFY_COMPLETE);
        progress.setPercent(50);
        return true;
    }

    // ---------------------------------------------------------------- delete

    private boolean deleteContent(UpdateProgress progress) {
        LOGGER.info("Start Delete Content");
        UserMessageManager.info(MessageKey.DELETE_CONTENT);
        StartupMessageManager.addModMessage("Start Delete Content");

        List<DeleteContentEntry> entries = AE2TextureUpdater.CONFIG.modify.deleteContentList.entries;
        if (entries.isEmpty()) {
            LOGGER.info("No content to delete");
            StartupMessageManager.addModMessage("No content to delete");
            UserMessageManager.info(MessageKey.NO_CONTENT_TO_DELETE);
        } else {
            for (DeleteContentEntry entry : entries) {
                if (!requireAssetsPath(entry.getPath(), MessageKey.DELETE_NOT_IN_ASSETS_DIR_FILE_WARN)) {
                    continue;
                }
                Path path = paths.resolvedInExtracted(stripAssetsPrefix(entry.getPath()));
                if (!Files.exists(path)) {
                    LOGGER.warn("The file does not exist");
                    UserMessageManager.warning(MessageKey.FILE_NOT_EXISTS);
                    continue;
                }
                if (Files.isDirectory(path)) {
                    if (!deleteRecursively(path, progress)) {
                        return false;
                    }
                } else if (!deleteFile(path, progress)) {
                    return false;
                }
            }
        }
        StartupMessageManager.addModMessage("Delete Content Completed");
        UserMessageManager.info(MessageKey.DELETE_CONTENT_COMPLETE);
        LOGGER.info("Delete Content Completed");
        progress.setPercent(75);
        return true;
    }

    // ----------------------------------------------------------- modid mapping

    private boolean mapModIds(UpdateProgress progress) {
        LOGGER.info("Start mapping ModID");
        UserMessageManager.info(MessageKey.MAPPING_MODID);
        StartupMessageManager.addModMessage("Start mapping ModID");

        List<ModIdMappingEntry> entries = AE2TextureUpdater.CONFIG.general.modIdMapping.entries;
        if (entries.isEmpty()) {
            LOGGER.info("No ModIDs need to be mapped");
            UserMessageManager.info(MessageKey.NO_MODIDS_TO_MAP);
            StartupMessageManager.addModMessage("No ModIDs need to be mapped");
            return true;
        }

        List<String> installedModIds = installedModIds(entries);
        for (String oldModId : installedModIds) {
            if (!mapSingleMod(oldModId, progress)) {
                return false;
            }
        }

        LOGGER.info("Map ModID Completed");
        UserMessageManager.info(MessageKey.MAP_MODID_COMPLETE);
        StartupMessageManager.addModMessage("Map ModID Completed");
        return true;
    }

    /** Returns the configured old mod ids that are present in the current mod list, in config order. */
    private static List<String> installedModIds(List<ModIdMappingEntry> entries) {
        List<String> result = new ArrayList<>();
        for (ModIdMappingEntry entry : entries) {
            String oldModId = entry.getOldModId();
            boolean installed = ModList.get().getMods().stream()
                    .anyMatch(info -> info.getModId().equals(oldModId));
            if (installed) {
                if (!result.contains(oldModId)) {
                    result.add(oldModId);
                }
            } else {
                LOGGER.info("The {} mod not found, skip!", oldModId);
                UserMessageManager.info(MessageKey.MODID_NOT_FOUND);
                StartupMessageManager.addModMessage(String.format("The %s mod not found, skip!", oldModId));
            }
        }
        return result;
    }

    private boolean mapSingleMod(String oldModId, UpdateProgress progress) {
        String newModId = AE2TextureUpdater.CONFIG.general.modIdMapping.entries.stream()
                .filter(entry -> entry.getOldModId().equals(oldModId))
                .findFirst()
                .map(ModIdMappingEntry::getNewModId)
                .orElse(oldModId);

        LOGGER.info("Find the new ModID for Mod {} and start the replacement", oldModId);
        StartupMessageManager.addModMessage(String.format("Find the new ModID for Mod %s and start the replacement", oldModId));
        LOGGER.info("Applying ModID mapping: {} -> {}", newModId, oldModId);
        UserMessageManager.info(MessageKey.APPLY_MODID, newModId, oldModId);

        Path sourcePath = paths.resolvedInExtracted(newModId);
        if (!Files.exists(sourcePath)) {
            LOGGER.error("No source mod found, possibly the mod download failed");
            UserMessageManager.info(MessageKey.UNABLE_TO_FIND_SOURCE_MOD_FILE);
            StartupMessageManager.addModMessage("No source mod found, possibly the mod download failed");
            return false;
        }

        Path targetPath = paths.resolvedInExtracted(oldModId);
        try {
            Files.createDirectories(targetPath.getParent());
            FileUtil.deleteDirectory(targetPath);
            Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Moved directory: {} -> {}", newModId, oldModId);
            UserMessageManager.info(MessageKey.MOVE_DIR, newModId, oldModId);
            replaceModIdInTextFiles(targetPath, newModId, oldModId);
            return true;
        } catch (IOException e) {
            UserMessageManager.error(MessageKey.UNKNOWN_ERROR, e.getMessage());
            LOGGER.error("Failed to apply ModID: {}", e.getMessage());
            return false;
        }
    }

    /** Rewrites every occurrence of {@code newModId} with {@code oldModId} in .json/.mcmeta files. */
    private static void replaceModIdInTextFiles(Path root, String newModId, String oldModId) throws IOException {
        String[] extensions = {".json", ".mcmeta"};
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String fileName = file.toString();
                for (String ext : extensions) {
                    if (fileName.endsWith(ext)) {
                        String content = Files.readString(file);
                        String newContent = content.replaceAll(newModId, oldModId);
                        if (!content.equals(newContent)) {
                            Files.writeString(file, newContent);
                        }
                        break;
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    // ---------------------------------------------------------------- helpers

    private static boolean requireAssetsPath(String path, MessageKey warnKey) {
        if (path != null && path.startsWith(ASSETS_PREFIX)) {
            return true;
        }
        LOGGER.warn("You can only operate on files in the assets directory");
        UserMessageManager.warning(warnKey);
        return false;
    }

    private static String stripAssetsPrefix(String path) {
        return path.substring(ASSETS_PREFIX.length());
    }

    private static boolean isDirectory(Path path) {
        return Files.exists(path) && Files.isDirectory(path);
    }

    private static boolean isRegularFile(Path path) {
        return Files.exists(path) && Files.isRegularFile(path);
    }

    private static boolean writeFile(Path path, String content, boolean overwrite, UpdateProgress progress) {
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            if (overwrite) {
                Files.writeString(path, content, StandardCharsets.UTF_8,
                        StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            } else {
                Files.writeString(path, content, StandardCharsets.UTF_8);
            }
            return true;
        } catch (IOException e) {
            LOGGER.error("Error creating or writing files, details: {}", e.getMessage());
            UserMessageManager.error(MessageKey.UNKNOWN_ERROR, e.getMessage());
            return false;
        }
    }

    private static boolean deleteFile(Path path, UpdateProgress progress) {
        try {
            Files.delete(path);
            return true;
        } catch (IOException e) {
            LOGGER.error("An error occurred when deleting a file or folder, detail: {}", e.getMessage());
            UserMessageManager.error(MessageKey.UNKNOWN_ERROR, e.getMessage());
            return false;
        }
    }
}
