package net.xuyifei.ae2_texture_updater;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.StartupMessageManager;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.progress.ProgressMeter;
import net.xuyifei.ae2_texture_updater.config.ModConfig;
import net.xuyifei.ae2_texture_updater.download.DownloadManager;
import net.xuyifei.ae2_texture_updater.download.ModListManager;
import net.xuyifei.ae2_texture_updater.message.MessageKey;
import net.xuyifei.ae2_texture_updater.message.UserMessageManager;
import net.xuyifei.ae2_texture_updater.update.ExtractionStep;
import net.xuyifei.ae2_texture_updater.update.ModificationStep;
import net.xuyifei.ae2_texture_updater.update.PackagingStep;
import net.xuyifei.ae2_texture_updater.update.UpdatePaths;
import net.xuyifei.ae2_texture_updater.update.UpdatePipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mod entry point. Registers the config screen and, unless the trigger mode is
 * {@link ModConfig.TriggerMode#NEVER}, runs the texture update pipeline during startup:
 * scan mods -> download -> extract -> modify -> package.
 */
@Mod(AE2TextureUpdater.MODID)
public class AE2TextureUpdater {
    public static final String MODID = "ae2_texture_updater";
    public static final Logger LOGGER = LoggerFactory.getLogger("AE2TextureUpdater");

    public static ModConfig CONFIG;

    public AE2TextureUpdater(FMLJavaModLoadingContext context) {
        registerConfig(context);

        StartupMessageManager.addModMessage("AE2 Textures Updater");
        UserMessageManager.info(MessageKey.READY);

        if (CONFIG.general.triggerMode != ModConfig.TriggerMode.NEVER) {
            runUpdate();
        } else {
            StartupMessageManager.addModMessage("Since the trigger mode is selected to never trigger, the update will not be triggered this time");
            UserMessageManager.info(MessageKey.NOT_TRIGGERED);
        }
    }

    private void registerConfig(FMLJavaModLoadingContext context) {
        AutoConfig.register(ModConfig.class, GsonConfigSerializer::new);
        CONFIG = AutoConfig.getConfigHolder(ModConfig.class).getConfig();
        context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mcClient, screen) ->
                        AutoConfig.getConfigScreen(ModConfig.class, screen).get()));
    }

    private static void runUpdate() {
        ProgressMeter progress1 = StartupMessageManager.addProgressBar("1/5 Scanning Mods", 1);
        UserMessageManager.info(MessageKey.CHECK_MODLIST);
        LOGGER.info("Start Scanning Mods");

        boolean needUpdate = ModListManager.scanMods();
        progress1.complete();
        LOGGER.info("Scan Mods Completed");
        UserMessageManager.info(MessageKey.CHECK_MODLIST_COMPLETE);

        boolean triggered = needUpdate && CONFIG.general.triggerMode == ModConfig.TriggerMode.ON_MOD_CHANGE
                || CONFIG.general.triggerMode == ModConfig.TriggerMode.ON_STARTUP;
        if (!triggered) {
            StartupMessageManager.addModMessage("No changes were found in the mod environment or errors occurred during detection");
            UserMessageManager.info(MessageKey.NOT_TRIGGERED);
            return;
        }

        UserMessageManager.info(MessageKey.HAS_BEEN_TRIGGERED);
        UpdatePaths paths = new UpdatePaths();
        UpdatePipeline pipeline = new UpdatePipeline();

        pipeline.run(MessageKey.ERROR_ON_EXTRACTED_JAR,
                () -> download() && new ExtractionStep(paths).run());
        pipeline.run(MessageKey.ERROR_ON_MODIFY_CONTENT,
                () -> new ModificationStep(paths).run());
        pipeline.run(MessageKey.UNABLE_TO_PACKING_FILES,
                () -> new PackagingStep(paths, FMLPaths.GAMEDIR.get().resolve("resourcepacks")).run());
    }

    private static boolean download() {
        ProgressMeter progress2 = StartupMessageManager.addProgressBar("2/5 Downloading Mods", 100);
        UserMessageManager.info(MessageKey.START_DOWNLOAD_MODS);
        LOGGER.info("Start Downloading Mods");

        boolean canContinue = DownloadManager.startDownload(progress2);

        LOGGER.info("Download Mods Completed");
        UserMessageManager.info(MessageKey.DOWNLOAD_MODS_COMPLETE);
        progress2.complete();

        if (!canContinue) {
            StartupMessageManager.addModMessage("Download Mods failed!");
            UserMessageManager.error(MessageKey.DOWNLOAD_MODS_FAILED);
            return false;
        }
        return true;
    }
}
