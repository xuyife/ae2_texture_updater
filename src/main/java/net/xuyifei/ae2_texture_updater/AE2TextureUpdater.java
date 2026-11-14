package net.xuyifei.ae2_texture_updater;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.StartupMessageManager;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.xuyifei.ae2_texture_updater.config.ModConfig;
import net.xuyifei.ae2_texture_updater.gui.BackgroundUpdateHandler;
import net.xuyifei.ae2_texture_updater.gui.UpdateAvailableOverlay;
import net.xuyifei.ae2_texture_updater.message.MessageKey;
import net.xuyifei.ae2_texture_updater.message.UserMessageManager;
import net.xuyifei.ae2_texture_updater.update.BackgroundUpdateState;
import net.xuyifei.ae2_texture_updater.update.UpdateCancelledException;
import net.xuyifei.ae2_texture_updater.update.UpdateChecker;
import net.xuyifei.ae2_texture_updater.update.UpdateReporter;
import net.xuyifei.ae2_texture_updater.update.UpdateRunner;
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

        boolean startupMode = CONFIG.general.runMode == ModConfig.RunMode.STARTUP;
        // Printed so a log always shows which mode actually took effect: a config file written
        // before runMode existed silently falls back to STARTUP, which looks like the setting
        // being ignored.
        LOGGER.info("Run mode: {}, trigger mode: {}", CONFIG.general.runMode, CONFIG.general.triggerMode);
        if (CONFIG.general.triggerMode != ModConfig.TriggerMode.NEVER && startupMode) {
            runUpdate();
        } else if (CONFIG.general.triggerMode == ModConfig.TriggerMode.NEVER) {
            StartupMessageManager.addModMessage("Since the trigger mode is selected to never trigger, the update will not be triggered this time");
            UserMessageManager.info(MessageKey.NOT_TRIGGERED);
        }
        // In background mode the pipeline is started from the title screen instead (see BackgroundUpdateHandler).

        // Startup mode: the pipeline just finished, so the check cannot compete with downloads.
        // Background mode: nothing has run yet, and a network call during loading is exactly what
        // this mode is supposed to avoid — the check is started from the title screen instead.
        if (startupMode) {
            startVersionCheck();
        }
    }

    /** Registers the listener once and starts the version query on a daemon thread. */
    public static void startVersionCheck() {
        if (!CONFIG.general.checkForUpdates) {
            return;
        }
        UpdateChecker.addListener(AE2TextureUpdater::onVersionCheckResult);
        UpdateChecker.checkAsync();
    }

    /** Runs the pipeline on a daemon thread with progress reported to the corner panel. */
    public static void runInBackground() {
        BackgroundUpdateState.begin();
        Thread thread = new Thread(() -> {
            UpdateRunner.Outcome outcome;
            try {
                outcome = UpdateRunner.run(new BackgroundUpdateState.Reporter());
            } catch (UpdateCancelledException e) {
                outcome = UpdateRunner.Outcome.CANCELLED;
            } finally {
                BackgroundUpdateState.finish();
            }
            BackgroundUpdateHandler.onRunFinished(outcome);
        }, "ae2-texture-updater-background");
        thread.setDaemon(true);
        thread.start();
    }

    /** Called from the check thread once a result exists; the overlay only stores it. */
    private static void onVersionCheckResult() {
        UpdateChecker.Result result = UpdateChecker.result();
        if (result != null && result.newer() && CONFIG.general.checkForUpdates) {
            UpdateAvailableOverlay.announce(result);
        }
    }

    /** Persists the config, used when the player's choice has to survive a restart. */
    public static void saveConfig() {
        AutoConfig.getConfigHolder(ModConfig.class).save();
    }

    private void registerConfig(FMLJavaModLoadingContext context) {
        AutoConfig.register(ModConfig.class, GsonConfigSerializer::new);
        CONFIG = AutoConfig.getConfigHolder(ModConfig.class).getConfig();
        context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mcClient, screen) ->
                        AutoConfig.getConfigScreen(ModConfig.class, screen).get()));
    }

    private static void runUpdate() {
        UpdateRunner.Outcome outcome = UpdateRunner.run(new UpdateReporter.Startup());
        if (outcome == UpdateRunner.Outcome.SKIPPED) {
            StartupMessageManager.addModMessage("No changes were found in the mod environment or errors occurred during detection");
            UserMessageManager.info(MessageKey.NOT_TRIGGERED);
        }
    }
}