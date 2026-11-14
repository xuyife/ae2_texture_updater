package net.xuyifei.ae2_texture_updater.update;

import net.minecraftforge.fml.loading.FMLPaths;
import net.xuyifei.ae2_texture_updater.AE2TextureUpdater;
import net.xuyifei.ae2_texture_updater.config.ModConfig;
import net.xuyifei.ae2_texture_updater.download.DownloadManager;
import net.xuyifei.ae2_texture_updater.download.ModListManager;
import net.xuyifei.ae2_texture_updater.message.MessageKey;
import net.xuyifei.ae2_texture_updater.message.UserMessageManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The texture update pipeline, decoupled from the FML loading screen.
 *
 * <p>Both run modes call {@link #run(UpdateReporter)}: during startup with a
 * {@link UpdateReporter.Startup} (progress on the loading screen, no cancel), or
 * in the background with {@link BackgroundUpdateState.Reporter} (progress in the
 * corner panel, cancellable). The steps themselves are identical.</p>
 */
public final class UpdateRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger("AE2TextureUpdater");

    /** Outcome of one run, used by the caller to pick the notification to show. */
    public enum Outcome { SUCCESS, FAILED, SKIPPED, CANCELLED }

    private UpdateRunner() {
    }

    public static Outcome run(UpdateReporter reporter) {
        UserMessageManager.info(MessageKey.CHECK_MODLIST);
        LOGGER.info("Start Scanning Mods");

        UpdateReporter.Stage scan = reporter.stage("1/5 Scanning Mods", 1);
        boolean needUpdate;
        try {
            needUpdate = ModListManager.scanMods();
        } finally {
            scan.complete();
        }
        LOGGER.info("Scan Mods Completed");
        UserMessageManager.info(MessageKey.CHECK_MODLIST_COMPLETE);

        boolean triggered = needUpdate && AE2TextureUpdater.CONFIG.general.triggerMode == ModConfig.TriggerMode.ON_MOD_CHANGE
                || AE2TextureUpdater.CONFIG.general.triggerMode == ModConfig.TriggerMode.ON_STARTUP;
        if (!triggered) {
            UserMessageManager.info(MessageKey.NOT_TRIGGERED);
            return Outcome.SKIPPED;
        }

        UserMessageManager.info(MessageKey.HAS_BEEN_TRIGGERED);
        UpdatePaths paths = new UpdatePaths();
        UpdatePipeline pipeline = new UpdatePipeline();

        try {
            pipeline.run(MessageKey.ERROR_ON_EXTRACTED_JAR,
                    () -> download(reporter) && new ExtractionStep(paths).run(reporter));
            pipeline.run(MessageKey.ERROR_ON_MODIFY_CONTENT,
                    () -> new ModificationStep(paths).run(reporter));
            pipeline.run(MessageKey.ERROR_ON_GUI_MERGE,
                    () -> new GuiMergeStep(paths).run(reporter));
            pipeline.run(MessageKey.UNABLE_TO_PACKING_FILES,
                    () -> new PackagingStep(paths, FMLPaths.GAMEDIR.get().resolve("resourcepacks")).run(reporter));
        } catch (UpdateCancelledException e) {
            LOGGER.info("Update cancelled by the player; clearing the dynamic cache");
            UserMessageManager.info(MessageKey.UPDATE_CANCELLED);
            CacheCleaner.clearDynamic();
            return Outcome.CANCELLED;
        }

        return pipeline.failed() ? Outcome.FAILED : Outcome.SUCCESS;
    }

    private static boolean download(UpdateReporter reporter) {
        UserMessageManager.info(MessageKey.START_DOWNLOAD_MODS);
        LOGGER.info("Start Downloading Mods");

        UpdateReporter.Stage stage = reporter.stage("2/5 Downloading Mods", 100);
        boolean canContinue;
        try {
            canContinue = DownloadManager.startDownload(reporter);
        } finally {
            stage.complete();
        }

        LOGGER.info("Download Mods Completed");
        UserMessageManager.info(MessageKey.DOWNLOAD_MODS_COMPLETE);
        if (!canContinue) {
            UserMessageManager.error(MessageKey.DOWNLOAD_MODS_FAILED);
            return false;
        }
        return true;
    }
}
