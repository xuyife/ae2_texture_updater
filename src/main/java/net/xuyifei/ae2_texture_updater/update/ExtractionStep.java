package net.xuyifei.ae2_texture_updater.update;

import net.minecraftforge.fml.StartupMessageManager;
import net.minecraftforge.fml.loading.progress.ProgressMeter;
import net.xuyifei.ae2_texture_updater.message.MessageKey;
import net.xuyifei.ae2_texture_updater.message.UserMessageManager;
import net.xuyifei.ae2_texture_updater.utils.FileUtil;
import net.xuyifei.ae2_texture_updater.utils.JarExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Step 3/5: extracts {@code assets/<modId>} trees from every downloaded jar
 * into the {@code extracted} directory, one folder per mod id.
 */
public final class ExtractionStep {
    private static final Logger LOGGER = LoggerFactory.getLogger("AE2TextureUpdater");

    private final UpdatePaths paths;

    public ExtractionStep(UpdatePaths paths) {
        this.paths = paths;
    }

    public boolean run() {
        if (!Files.isDirectory(paths.filesDir())) {
            return true; // nothing downloaded, nothing to extract
        }

        ProgressMeter progress = StartupMessageManager.addProgressBar("3/5 Unzipping the Jar", 100);
        UserMessageManager.info(MessageKey.UNZIP_JAR);
        LOGGER.info("Start Unzipping the Jar");

        boolean ok = extractAll(progress);

        progress.complete();
        LOGGER.info("Unzip the Jar Completed");
        UserMessageManager.info(MessageKey.UNZIP_JAR_COMPLETE);
        return ok;
    }

    private boolean extractAll(ProgressMeter progress) {
        Path extractedDir = paths.extractedDir();
        FileUtil.cleanDirectory(extractedDir);

        try (Stream<Path> stream = Files.walk(paths.filesDir())) {
            List<Path> jars = stream
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith("download_") && name.endsWith(".jar");
                    })
                    .toList();

            for (Path jar : jars) {
                if (!extractJar(jar, extractedDir, progress)) {
                    return false;
                }
            }
            return true;
        } catch (IOException e) {
            LOGGER.error("Error occurred while traversing files, details: {}", e.getMessage());
            UserMessageManager.error(MessageKey.UNKNOWN_ERROR, e.getMessage());
            progress.complete();
            return false;
        }
    }

    private boolean extractJar(Path jar, Path extractedDir, ProgressMeter progress) {
        try {
            List<String> modDirs = JarExtractor.listSubDirectories(jar.toString(), "assets");
            modDirs.removeIf("minecraft"::equals);
            modDirs.removeIf(name -> isAssetlessAe2Addon(jar, name, progress));

            for (String modDir : modDirs) {
                JarExtractor.extractJar(jar.toString(), "assets/" + modDir, extractedDir.toString());
            }
            return true;
        } catch (IOException e) {
            LOGGER.error("Error occurred while traversing files, details: {}", e.getMessage());
            UserMessageManager.error(MessageKey.UNKNOWN_ERROR, e.getMessage());
            progress.complete();
            return false;
        }
    }

    /**
     * A mod is skipped when its {@code assets/ae2} tree carries no textures, lang or models,
     * or when probing the jar fails (reported as an error by the caller of the probe).
     */
    private boolean isAssetlessAe2Addon(Path jar, String name, ProgressMeter progress) {
        if (!"ae2".equals(name)) {
            return false;
        }
        try {
            List<String> subDirs = JarExtractor.listSubDirectories(jar.toString(), "assets/ae2");
            return !subDirs.contains("textures") && !subDirs.contains("lang") && !subDirs.contains("models");
        } catch (IOException e) {
            LOGGER.error("Error occurred while traversing files, details: {}", e.getMessage());
            UserMessageManager.error(MessageKey.UNKNOWN_ERROR, e.getMessage());
            progress.complete();
            return false;
        }
    }
}
