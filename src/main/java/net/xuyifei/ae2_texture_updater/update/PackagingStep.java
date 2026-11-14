package net.xuyifei.ae2_texture_updater.update;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraftforge.fml.StartupMessageManager;
import net.xuyifei.ae2_texture_updater.message.MessageKey;
import net.xuyifei.ae2_texture_updater.message.UserMessageManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.FileVisitResult;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Step 5/5: packages the {@code extracted} directory into a resource pack
 * ({@code pack.mcmeta} + {@code assets/...} entries) and copies it to {@code resourcepacks/}.
 */
public final class PackagingStep {
    private static final Logger LOGGER = LoggerFactory.getLogger("AE2TextureUpdater");

    /** Pack format for MC 1.20-1.20.1 resource packs. */
    private static final int PACK_FORMAT = 15;
    private static final String PACK_FILE_NAME = "AE2_Texture_Updater.zip";

    private final UpdatePaths paths;
    private final Path resourcePacksDir;

    public PackagingStep(UpdatePaths paths, Path resourcePacksDir) {
        this.paths = paths;
        this.resourcePacksDir = resourcePacksDir;
    }

    public boolean run(UpdateReporter reporter) {
        if (!Files.isDirectory(paths.extractedDir())) {
            StartupMessageManager.addModMessage("Error unzipping Jar");
            UserMessageManager.error(MessageKey.ERROR_ON_EXTRACTED_JAR);
            return false;
        }

        UpdateReporter.Stage progress = reporter.stage("5/5 Packaging Files", 100);
        UserMessageManager.info(MessageKey.PACK_RESOURCEPACK);
        LOGGER.info("Start Packaging Files");

        // Unloading and restoring the pack both reload all resources, which interrupts play.
        // Only ask when a reload would actually happen, and only in the background mode where
        // the player is likely mid-game.
        Path packFile = resourcePacksDir.resolve(PACK_FILE_NAME);
        boolean needsReload = ResourcePackGate.wouldReload(packFile);
        if (needsReload && BackgroundUpdateState.status() == BackgroundUpdateState.Status.RUNNING) {
            ResourcePackPrompt.askAndWait();
        }

        // The installed pack is held open while enabled; drop it before the file is replaced.
        List<String> releasedPack = ResourcePackGate.release(packFile);
        boolean ok;
        try {
            ok = packageFiles(progress, reporter);
        } finally {
            ResourcePackGate.restore(releasedPack);
        }

        if (ok) {
            LOGGER.info("Package Files Completed");
            UserMessageManager.info(MessageKey.PACK_RESOURCEPACK_COMPLETE);
        }
        progress.complete();
        return ok;
    }

    private boolean packageFiles(UpdateReporter.Stage progress, UpdateReporter reporter) {
        Path assetsPath = paths.extractedDir();
        try {
            Files.createDirectories(paths.cacheDir());
            Files.deleteIfExists(paths.packZip());
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(paths.packZip()))) {
                writePackMetadata(zos);
                if (!Files.isDirectory(assetsPath)) {
                    UserMessageManager.warning(MessageKey.NO_EXTRACTED_MOD_FOUND);
                    LOGGER.warn("extracted directory not found: {}", assetsPath);
                    return false;
                }
                zipTree(zos, assetsPath, reporter);
            }

            return installPack(progress);
        } catch (IOException e) {
            UserMessageManager.error(MessageKey.UNKNOWN_ERROR, e.getMessage());
            LOGGER.error("Error while packaging files, detail: {}", e.getMessage());
            progress.complete();
            return false;
        }
    }

    /**
     * Replaces the installed pack, retrying briefly.
     *
     * <p>{@link ResourcePackGate} already released the pack, but on Windows a handle can
     * linger for a moment after a reload (antivirus or the file indexer touching the file).
     * A short retry turns that race into a success instead of a failed update.</p>
     */
    private void replacePackFile(Path target) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                Files.deleteIfExists(target);
                Files.copy(paths.packZip(), target);
                return;
            } catch (IOException e) {
                last = e;
                LOGGER.warn("Unable to replace the resource pack (attempt {}): {}", attempt + 1, e.getMessage());
                try {
                    Thread.sleep(400L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        throw last != null ? last : new IOException("unable to replace " + target);
    }

    private static void writePackMetadata(ZipOutputStream zos) throws IOException {
        Gson gson = new Gson();
        JsonObject root = new JsonObject();
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", PACK_FORMAT);
        pack.addProperty("description",
                "This package is automatically generated by AE2 Texture Updater to update AE2's materials\n" +
                        "GUI assets include AE-Light-UI by LeeQianXi (LGPL-3.0, https://github.com/LeeQianXi/AE-1.20-UI)\n" +
                        "By XUYIFEI");
        root.add("pack", pack);

        ZipEntry mcmetaEntry = new ZipEntry("pack.mcmeta");
        mcmetaEntry.setTime(System.currentTimeMillis());
        zos.putNextEntry(mcmetaEntry);
        zos.write(gson.toJson(root).getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private static void zipTree(ZipOutputStream zos, Path assetsPath, UpdateReporter reporter) throws IOException {
        Files.walkFileTree(assetsPath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (!dir.equals(assetsPath)) {
                    String entryName = "assets/" + assetsPath.relativize(dir).toString().replace('\\', '/') + "/";
                    zos.putNextEntry(new ZipEntry(entryName));
                    zos.closeEntry();
                    LOGGER.debug("Adding directory: {}", entryName);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                reporter.checkCancelled();
                String entryName = "assets/" + assetsPath.relativize(file).toString().replace('\\', '/');
                ZipEntry entry = new ZipEntry(entryName);
                entry.setLastModifiedTime(Files.getLastModifiedTime(file));
                zos.putNextEntry(entry);
                Files.copy(file, zos);
                zos.closeEntry();
                LOGGER.debug("Adding file: {}", entryName);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                LOGGER.error("Failed to access: {}", file, exc);
                UserMessageManager.error(MessageKey.UNKNOWN_ERROR, exc.getMessage());
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private boolean installPack(UpdateReporter.Stage progress) {
        if (!Files.exists(paths.packZip())) {
            LOGGER.warn("Unable to package files");
            UserMessageManager.error(MessageKey.UNABLE_TO_PACKING_FILES);
            return false;
        }
        try {
            Files.createDirectories(resourcePacksDir);
            Path target = resourcePacksDir.resolve(PACK_FILE_NAME);
            replacePackFile(target);
            return true;
        } catch (IOException e) {
            UserMessageManager.error(MessageKey.UNKNOWN_ERROR, e.getMessage());
            LOGGER.error("Error while installing resource pack, detail: {}", e.getMessage());
            progress.complete();
            return false;
        }
    }
}