package net.xuyifei.ae2_texture_updater.update;

import com.google.gson.JsonParser;
import net.xuyifei.ae2_texture_updater.AE2TextureUpdater;
import net.xuyifei.ae2_texture_updater.download.GuiPackDownloader;
import net.xuyifei.ae2_texture_updater.message.MessageKey;
import net.xuyifei.ae2_texture_updater.message.UserMessageManager;
import net.xuyifei.ae2_texture_updater.utils.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Step 4.5: builds the GUI repair layer.
 *
 * <p>Works in three passes:</p>
 * <ol>
 *   <li><b>Namespace filter</b> — list the mod ids in {@code extracted/} (the
 *       installed mods), then keep only the community pack's namespaces that
 *       match; every other namespace is deleted.</li>
 *   <li><b>Per-namespace content filter</b> — whitelist: keep {@code screens/}
 *       and {@code textures/}; blacklist: inside {@code textures/} remove
 *       {@code block/} and {@code item/}; everything else is deleted.</li>
 *   <li><b>Overlay</b> — copy the filtered community pack over {@code extracted/}.</li>
 * </ol>
 *
 * <p>Before the overlay, the 1.21 extraction's own GUI subtrees (screens/guis/
 * gui/elements) are stripped: 1.21 screens JSONs would misalign or break GUIs
 * on 1.20.1, so only the community layer and the mods' own 1.20.1 GUIs remain.</p>
 */
public final class GuiMergeStep {
    private static final Logger LOGGER = LoggerFactory.getLogger("AE2TextureUpdater");

    /** Subtrees of the 1.21 extraction that belong to GUIs and must be stripped first. */
    private static final Set<String> STRIPPED_GUI_DIRS = Set.of("screens", "guis", "gui", "elements");

    /** Whitelist: the only subtrees kept from a community-pack namespace. */
    private static final Set<String> KEEP_DIRS = Set.of("screens", "textures");

    /** Blacklist inside the whitelisted textures/ tree (block/item stay from 1.20.1). */
    private static final Set<String> STRIPPED_TEXTURE_DIRS = Set.of("block", "item");

    private final UpdatePaths paths;

    public GuiMergeStep(UpdatePaths paths) {
        this.paths = paths;
    }

    /**
     * Runs the merge. Returns {@code true} even when the layer is disabled or
     * unavailable — a missing GUI layer is a warning, never a pipeline failure.
     * The meter is completed here when the step finishes.
     */
    public boolean run(net.minecraftforge.fml.loading.progress.ProgressMeter mainProgress) {
        try {
            return runInternal(mainProgress);
        } finally {
            if (mainProgress != null) {
                mainProgress.complete();
            }
        }
    }

    private boolean runInternal(net.minecraftforge.fml.loading.progress.ProgressMeter mainProgress) {
        if (!AE2TextureUpdater.CONFIG.guiMerge.enabled) {
            UserMessageManager.info(MessageKey.GUI_PACK_SKIPPED_DISABLED);
            return true;
        }

        GuiPackDownloader downloader = new GuiPackDownloader(paths.cacheDir());
        Path packRoot = downloader.downloadAndExtract(mainProgress);
        if (packRoot == null) {
            UserMessageManager.warning(MessageKey.GUI_MERGE_NO_PACK);
            LOGGER.warn("GUI pack unavailable; the generated pack will use the mods' own 1.20.1 GUIs");
            return true;
        }

        Path packAssets = locateAssets(packRoot);
        if (packAssets == null) {
            UserMessageManager.warning(MessageKey.GUI_MERGE_NO_PACK);
            LOGGER.warn("GUI pack has no assets/ directory at: {}", packRoot);
            return true;
        }

        // Pass 1: keep only namespaces matching the installed (extracted) mod ids.
        UserMessageManager.info(MessageKey.GUI_MERGE_SCANNING);
        Set<String> installed = installedNamespaces();
        int namespaces = filterNamespaces(packAssets, installed);
        if (namespaces == 0) {
            UserMessageManager.warning(MessageKey.GUI_MERGE_NO_PACK);
            LOGGER.warn("No community-pack namespace matches an installed mod; nothing to merge");
            return true;
        }

        // Pass 2: whitelist screens/+textures/, blacklist textures/block+item.
        UserMessageManager.info(MessageKey.GUI_MERGE_MERGING);
        int skippedJsons = applyContentFilter(packAssets);

        // The 1.21 extraction's own GUI subtrees would misalign screens on 1.20.1;
        // strip them so only the community layer (and the mods' 1.20.1 GUIs) remain.
        stripGuiSubtrees();

        // Pass 3: overlay the filtered community pack onto the extraction.
        int mergedFiles = overlay(packAssets);

        UserMessageManager.success(MessageKey.GUI_MERGE_COMPLETE, namespaces, mergedFiles, skippedJsons);
        LOGGER.info("GUI merge complete: {} namespaces, {} files merged, {} broken jsons skipped",
                namespaces, mergedFiles, skippedJsons);
        return true;
    }

    // ------------------------------------------------------- pass 1: namespaces

    /** Mod ids currently in {@code extracted/} — the installed mods to match against. */
    private Set<String> installedNamespaces() {
        Set<String> result = new HashSet<>();
        Path extracted = paths.extractedDir();
        if (!Files.isDirectory(extracted)) {
            return result;
        }
        try (Stream<Path> children = Files.list(extracted)) {
            children.filter(Files::isDirectory)
                    .forEach(dir -> result.add(dir.getFileName().toString()));
        } catch (IOException e) {
            LOGGER.warn("Failed listing extracted namespaces: {}", e.getMessage());
        }
        return result;
    }

    /** Deletes every community-pack namespace not present in {@code installed}; returns kept count. */
    private int filterNamespaces(Path packAssets, Set<String> installed) {
        int kept = 0;
        try (Stream<Path> namespaces = Files.list(packAssets)) {
            for (Path namespaceDir : namespaces.filter(Files::isDirectory).toList()) {
                String name = namespaceDir.getFileName().toString();
                if (installed.contains(name)) {
                    kept++;
                } else {
                    LOGGER.info("Dropping community-pack namespace {} (mod not installed)", name);
                    FileUtil.deleteDirectory(namespaceDir);
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Failed filtering community-pack namespaces: {}", e.getMessage());
        }
        return kept;
    }

    // --------------------------------------------------- pass 2: content filter

    /**
     * Applies the whitelist/blacklist to every kept namespace and validates
     * screens JSONs. Returns the number of broken JSONs removed.
     */
    private int applyContentFilter(Path packAssets) {
        int skippedJsons = 0;
        try (Stream<Path> namespaces = Files.list(packAssets)) {
            for (Path namespaceDir : namespaces.filter(Files::isDirectory).toList()) {
                try (Stream<Path> children = Files.list(namespaceDir)) {
                    for (Path child : children.toList()) {
                        String name = child.getFileName().toString();
                        if (!KEEP_DIRS.contains(name)) {
                            FileUtil.deleteDirectory(child);
                        }
                    }
                }
                Path textures = namespaceDir.resolve("textures");
                if (Files.isDirectory(textures)) {
                    try (Stream<Path> textureDirs = Files.list(textures)) {
                        for (Path dir : textureDirs.filter(Files::isDirectory).toList()) {
                            if (STRIPPED_TEXTURE_DIRS.contains(dir.getFileName().toString())) {
                                FileUtil.deleteDirectory(dir);
                            }
                        }
                    }
                }
                skippedJsons += removeBrokenScreensJsons(namespaceDir.resolve("screens"));
            }
        } catch (IOException e) {
            LOGGER.warn("Failed applying the content filter: {}", e.getMessage());
        }
        return skippedJsons;
    }

    /** Deletes screens JSONs that fail to parse; returns how many were removed. */
    private int removeBrokenScreensJsons(Path screensDir) {
        if (!Files.isDirectory(screensDir)) {
            return 0;
        }
        int removed = 0;
        try (Stream<Path> jsons = Files.walk(screensDir)) {
            for (Path json : jsons.filter(p -> p.toString().endsWith(".json")).toList()) {
                try {
                    JsonParser.parseString(Files.readString(json, StandardCharsets.UTF_8));
                } catch (Exception e) {
                    removed++;
                    Path namespace = screensDir.getParent();
                    UserMessageManager.warning(MessageKey.GUI_MERGE_BAD_JSON,
                            namespace.getFileName() + "/" + namespace.relativize(json),
                            e.getMessage());
                    LOGGER.warn("Removing broken screens JSON {}: {}", json, e.getMessage());
                    try {
                        Files.deleteIfExists(json);
                    } catch (IOException io) {
                        LOGGER.warn("Failed to delete broken JSON {}: {}", json, io.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Failed validating screens JSONs: {}", e.getMessage());
        }
        return removed;
    }

    // -------------------------------------------------- strip 1.21 gui subtrees

    /** Removes GUI subtrees (screens/, guis/, gui/, elements/) from every 1.21 namespace. */
    private void stripGuiSubtrees() {
        Path extracted = paths.extractedDir();
        if (!Files.isDirectory(extracted)) {
            return;
        }
        try (Stream<Path> namespaces = Files.list(extracted)) {
            for (Path namespaceDir : namespaces.filter(Files::isDirectory).toList()) {
                try (Stream<Path> children = Files.list(namespaceDir)) {
                    for (Path child : children.filter(Files::isDirectory).toList()) {
                        if (STRIPPED_GUI_DIRS.contains(child.getFileName().toString())) {
                            FileUtil.deleteDirectory(child);
                        }
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to strip GUI subtrees from 1.21 layer: {}", e.getMessage());
        }
    }

    // ------------------------------------------------------- pass 3: overlay

    /** Copies the whole filtered community pack over {@code extracted/}; returns file count. */
    private int overlay(Path packAssets) {
        int copied = 0;
        try (Stream<Path> files = Files.walk(packAssets)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String rel = packAssets.relativize(file).toString().replace('\\', '/');
                Path dest = paths.resolvedInExtracted(rel);
                try {
                    Files.createDirectories(dest.getParent());
                    Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
                    copied++;
                } catch (IOException e) {
                    LOGGER.error("Failed to overlay {}: {}", rel, e.getMessage());
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Failed overlaying the community pack: {}", e.getMessage());
        }
        return copied;
    }

    // ----------------------------------------------------------------- helpers

    /** Finds the assets/ root inside the unpacked community pack (top or one level deep). */
    private static Path locateAssets(Path packRoot) {
        Path direct = packRoot.resolve("assets");
        if (Files.isDirectory(direct)) {
            return direct;
        }
        try (Stream<Path> level1 = Files.list(packRoot)) {
            for (Path child : level1.filter(Files::isDirectory).toList()) {
                Path assets = child.resolve("assets");
                if (Files.isDirectory(assets)) {
                    return assets;
                }
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }
}
