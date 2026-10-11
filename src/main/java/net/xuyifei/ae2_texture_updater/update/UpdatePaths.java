package net.xuyifei.ae2_texture_updater.update;

import net.xuyifei.ae2_texture_updater.download.ModListManager;

import java.nio.file.Path;

/**
 * Filesystem layout of the update cache, derived from {@link ModListManager#CACHE_DIR}:
 * <pre>
 * cache/ae2_texture_updater/
 * ├── files/        downloaded jars (download_*.jar)
 * ├── extracted/    jar contents unpacked per mod id
 * └── AE2_Texture_Updater.zip  packaged resource pack
 * </pre>
 */
public final class UpdatePaths {
    private final Path cacheDir;

    public UpdatePaths() {
        this(ModListManager.CACHE_DIR);
    }

    public UpdatePaths(Path cacheDir) {
        this.cacheDir = cacheDir;
    }

    public Path cacheDir() {
        return cacheDir;
    }

    public Path filesDir() {
        return cacheDir.resolve("files");
    }

    public Path extractedDir() {
        return cacheDir.resolve("extracted");
    }

    public Path resolvedInExtracted(String relativePath) {
        return extractedDir().resolve(relativePath);
    }

    /** The packaged resource pack inside the cache directory. */
    public Path packZip() {
        return cacheDir.resolve("AE2_Texture_Updater.zip");
    }
}
