package net.xuyifei.ae2_texture_updater.update;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;
import net.xuyifei.ae2_texture_updater.AE2TextureUpdater;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Temporarily unloads the generated resource pack so its zip can be replaced.
 *
 * <p>An enabled pack is held open by {@code FilePackResources}, which keeps a
 * {@link java.nio.file.FileSystem} on the zip. On Windows that open handle makes the file
 * unsupported for writing, so replacing it fails with "another program is using this file".
 * Unload + reload closes the handle; the pack is re-enabled and reloaded afterwards so the
 * player also picks up the new textures without restarting.</p>
 *
 * <p>Only relevant in background mode: during startup the pack list has not been read yet,
 * so the gate finds the pack disabled and does nothing.</p>
 */
public final class ResourcePackGate {
    /** How long to wait for a reload before giving up and letting the write fail loudly. */
    private static final long RELOAD_TIMEOUT_SECONDS = 120L;

    private ResourcePackGate() {
    }

    /** Whether {@link #release(Path)} would actually trigger a reload for this file. */
    public static boolean wouldReload(Path packFile) {
        PackRepository repository = Minecraft.getInstance().getResourcePackRepository();
        String packId = packId(packFile);
        return repository.isAvailable(packId) && repository.getSelectedIds().contains(packId);
    }

    /**
     * Disables and reloads the pack when it is currently enabled.
     *
     * @return the pack ids that were selected before (to be passed to {@link #restore(List)}),
     *         or {@code null} when nothing had to change
     */
    public static List<String> release(Path packFile) {
        Minecraft minecraft = Minecraft.getInstance();
        String packId = packId(packFile);
        PackRepository repository = minecraft.getResourcePackRepository();
        if (!repository.isAvailable(packId)) {
            return null;
        }
        List<String> previous = new ArrayList<>(repository.getSelectedIds());
        if (!previous.contains(packId)) {
            return null;
        }

        List<String> without = new ArrayList<>(previous);
        without.remove(packId);
        AE2TextureUpdater.LOGGER.info("Unloading {} so the pack file can be replaced", packId);
        if (!applySelectionAndReload(minecraft, without)) {
            return null;
        }
        return previous;
    }

    /** Re-enables the pack and reloads, so the freshly written textures take effect. */
    public static void restore(List<String> previous) {
        if (previous == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!applySelectionAndReload(minecraft, previous)) {
            AE2TextureUpdater.LOGGER.warn("Unable to re-enable the resource pack after writing it");
        }
    }

    /** Vanilla pack id for a zip in the resourcepacks folder. */
    public static String packId(Path packFile) {
        return "file/" + packFile.getFileName();
    }

    /**
     * Applies the selection on the main thread and waits for the reload to finish.
     *
     * <p>The wait is on this thread (a background update thread), so blocking here does not
     * stall the client; the reload itself already runs on the background executor.</p>
     */
    private static boolean applySelectionAndReload(Minecraft minecraft, List<String> selection) {
        CompletableFuture<Void> reload = new CompletableFuture<>();
        minecraft.execute(() -> {
            try {
                minecraft.getResourcePackRepository().setSelected(selection);
                minecraft.reloadResourcePacks().whenComplete((ignored, error) -> {
                    if (error != null) {
                        reload.completeExceptionally(error);
                    } else {
                        reload.complete(null);
                    }
                });
            } catch (Exception e) {
                reload.completeExceptionally(e);
            }
        });
        try {
            reload.get(RELOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            AE2TextureUpdater.LOGGER.warn("Resource pack reload failed or timed out: {}", e.getMessage());
            return false;
        }
    }
}