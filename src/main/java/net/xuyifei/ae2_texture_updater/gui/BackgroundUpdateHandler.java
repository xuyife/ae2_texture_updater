package net.xuyifei.ae2_texture_updater.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.xuyifei.ae2_texture_updater.AE2TextureUpdater;
import net.xuyifei.ae2_texture_updater.config.ModConfig;
import net.xuyifei.ae2_texture_updater.message.UserMessageManager;
import net.xuyifei.ae2_texture_updater.update.UpdateRunner;

/**
 * Starts the background update run on the first title screen and reports its outcome.
 *
 * <p>The run is triggered here because "after the client finished loading" is exactly
 * the moment the title screen appears. The outcome toast is shown regardless of
 * whether the player hid the progress panel — only a cancellation stays silent.</p>
 */
@Mod.EventBusSubscriber(modid = AE2TextureUpdater.MODID, value = Dist.CLIENT)
public final class BackgroundUpdateHandler {

    /** Only the first title screen of this session starts the run. */
    private static boolean started = false;

    private BackgroundUpdateHandler() {
    }

    @SubscribeEvent
    public static void onTitleScreenInit(ScreenEvent.Init event) {
        if (started || !(event.getScreen() instanceof TitleScreen)) {
            return;
        }
        started = true;
        if (AE2TextureUpdater.CONFIG.general.runMode != ModConfig.RunMode.BACKGROUND) {
            return;
        }
        // Both the pipeline and the version check belong to "after the game finished loading"
        // in this mode; starting either one earlier shows network activity on the loading screen.
        // The version check is independent of the trigger mode: "never trigger" silences the
        // texture update, not the notice about a newer release.
        AE2TextureUpdater.startVersionCheck();
        if (AE2TextureUpdater.CONFIG.general.triggerMode == ModConfig.TriggerMode.NEVER) {
            return;
        }
        AE2TextureUpdater.runInBackground();
    }

    /**
     * Shows the vanilla toast with the run outcome. Cancellation is deliberately
     * silent: the player chose to stop, so nothing needs to be reported.
     */
    public static void onRunFinished(UpdateRunner.Outcome outcome) {
        if (outcome == UpdateRunner.Outcome.CANCELLED || outcome == UpdateRunner.Outcome.SKIPPED) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            Component title = Component.translatable("toast.ae2_texture_updater.background.title");
            Component detail;
            if (outcome == UpdateRunner.Outcome.SUCCESS) {
                int warnings = UserMessageManager.warningCount();
                int errors = UserMessageManager.errorCount();
                detail = Component.translatable("toast.ae2_texture_updater.background.success", errors, warnings);
            } else {
                detail = Component.translatable("toast.ae2_texture_updater.background.failed");
            }
            SystemToast.add(minecraft.getToasts(), SystemToast.SystemToastIds.PERIODIC_NOTIFICATION, title, detail);
        });
    }
}