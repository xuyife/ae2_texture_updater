package net.xuyifei.ae2_texture_updater.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.xuyifei.ae2_texture_updater.AE2TextureUpdater;
import net.xuyifei.ae2_texture_updater.message.UserMessageManager;

/**
 * Shows a toast on the first title screen after startup when the texture update
 * pipeline recorded warnings or errors, so players notice failures that would
 * otherwise only appear in the log file.
 */
@Mod.EventBusSubscriber(modid = AE2TextureUpdater.MODID, value = Dist.CLIENT)
public final class UpdateResultToastHandler {

    /** Only the first title screen of this session shows the toast. */
    private static boolean toastShown = false;

    private UpdateResultToastHandler() {
    }

    @SubscribeEvent
    public static void onTitleScreenInit(ScreenEvent.Init event) {
        if (toastShown || !(event.getScreen() instanceof TitleScreen)) {
            return;
        }
        toastShown = true;
        if (!UserMessageManager.hasProblems()) {
            return;
        }
        int warnings = UserMessageManager.warningCount();
        int errors = UserMessageManager.errorCount();
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            ToastComponent toasts = minecraft.getToasts();
            Component title = Component.translatable("toast.ae2_texture_updater.update_problems.title");
            Component detail = Component.translatable("toast.ae2_texture_updater.update_problems.detail", errors, warnings);
            SystemToast.add(toasts, SystemToast.SystemToastIds.PERIODIC_NOTIFICATION, title, detail);
        });
    }
}
