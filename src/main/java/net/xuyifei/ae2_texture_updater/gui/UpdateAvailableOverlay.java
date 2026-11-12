package net.xuyifei.ae2_texture_updater.gui;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.xuyifei.ae2_texture_updater.AE2TextureUpdater;
import net.xuyifei.ae2_texture_updater.config.ModConfig;
import net.xuyifei.ae2_texture_updater.update.UpdateChecker;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Toast-like announcement of a newer release, pinned to the top-right corner.
 *
 * <p>It is drawn over the current frame instead of being a
 * {@link net.minecraft.client.gui.screens.Screen}, so the player keeps playing and
 * the panel survives clicks anywhere else. Only clicks that land on one of its three
 * buttons are consumed; every other mouse and key input reaches the game below.</p>
 *
 * <p>The geometry follows the vanilla toast slot: {@link #PANEL_WIDTH} matches the
 * 160px toast width and the panel sits at the very corner, so it lines up with the
 * toasts that occupy the same rows.</p>
 */
@Mod.EventBusSubscriber(modid = AE2TextureUpdater.MODID, value = Dist.CLIENT)
public final class UpdateAvailableOverlay {
    /** How long "later" keeps the panel away. */
    private static final long SNOOZE_MILLIS = 3L * 24L * 60L * 60L * 1000L;

    /** Same width as a vanilla toast. */
    private static final int PANEL_WIDTH = 160;
    private static final int PANEL_HEIGHT = 44;
    private static final int TEXT_LEFT = 8;
    private static final int TITLE_TOP = 5;
    private static final int MESSAGE_TOP = 17;
    private static final int BUTTON_HEIGHT = 12;
    private static final int BUTTON_TOP = 29;
    private static final int BUTTON_SPACING = 3;

    private static final int COLOUR_PANEL = 0xC0101010;
    private static final int COLOUR_BUTTON = 0xFF2A2A2A;
    private static final int COLOUR_BUTTON_HOVER = 0xFF3F6F3F;
    private static final int COLOUR_TITLE = 0x55FF55;
    private static final int COLOUR_TEXT = 0xFFFFFF;

    /** Non-null while the panel is on screen. */
    private static volatile UpdateChecker.Result pending;

    private UpdateAvailableOverlay() {
    }

    /** Shows the panel unless this version was ignored or "later" is still active. */
    public static void announce(UpdateChecker.Result result) {
        ModConfig.General general = AE2TextureUpdater.CONFIG.general;
        if (result.latestVersion().equals(general.ignoredVersion)
                || System.currentTimeMillis() < general.snoozeUntil) {
            return;
        }
        pending = result;
    }

    /** In-game: no screen is open, so the HUD hook is the only place to draw. */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (pending == null || minecraft.screen != null) {
            return;
        }
        Window window = minecraft.getWindow();
        draw(event.getGuiGraphics(), window.getGuiScaledWidth(),
                mouseX(window), mouseY(window), minecraft);
    }

    /** With a screen open the HUD is already painted, so draw on top of the screen instead. */
    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (pending == null) {
            return;
        }
        draw(event.getGuiGraphics(), event.getScreen().width,
                event.getMouseX(), event.getMouseY(), Minecraft.getInstance());
    }

    private static void draw(GuiGraphics graphics, int guiWidth, double mouseX, double mouseY,
                             Minecraft minecraft) {
        UpdateChecker.Result result = pending;
        if (result == null) {
            return;
        }
        // Flush with the top-right corner, the same slot vanilla toasts use.
        int left = guiWidth - PANEL_WIDTH;
        int top = 0;

        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, COLOUR_PANEL);
        graphics.drawString(minecraft.font,
                Component.literal("AE2 Texture Updater"),
                left + TEXT_LEFT, top + TITLE_TOP, COLOUR_TITLE);
        graphics.drawString(minecraft.font,
                Component.translatable("overlay.ae2_texture_updater.update_available.line", result.latestVersion())
                        .withStyle(ChatFormatting.WHITE),
                left + TEXT_LEFT, top + MESSAGE_TOP, COLOUR_TEXT);

        String[] labels = {
                "overlay.ae2_texture_updater.update_available.button.download",
                "overlay.ae2_texture_updater.update_available.button.later",
                "overlay.ae2_texture_updater.update_available.button.ignore"
        };
        int buttonWidth = buttonWidth();
        for (int i = 0; i < labels.length; i++) {
            int buttonLeft = buttonLeft(guiWidth, i, buttonWidth);
            int buttonTop = top + BUTTON_TOP;
            boolean hovered = isInside(mouseX, mouseY, buttonLeft, buttonTop, buttonWidth, BUTTON_HEIGHT);
            graphics.fill(buttonLeft, buttonTop, buttonLeft + buttonWidth, buttonTop + BUTTON_HEIGHT,
                    hovered ? COLOUR_BUTTON_HOVER : COLOUR_BUTTON);
            graphics.drawCenteredString(minecraft.font,
                    Component.translatable(labels[i]).withStyle(ChatFormatting.WHITE),
                    buttonLeft + buttonWidth / 2, buttonTop + 2, COLOUR_TEXT);
        }
    }

    /**
     * Raw input arrives before any screen processes it, so one hook covers both the
     * in-game HUD and screens. Cancelling here swallows only the click that hit a button.
     */
    @SubscribeEvent
    public static void onMousePressed(InputEvent.MouseButton.Pre event) {
        if (pending == null || event.getButton() != 0) {
            return;
        }
        Window window = Minecraft.getInstance().getWindow();
        int guiWidth = window.getGuiScaledWidth();
        double mouseX = mouseX(window);
        double mouseY = mouseY(window);
        int buttonWidth = buttonWidth();
        for (int i = 0; i < 3; i++) {
            int buttonLeft = buttonLeft(guiWidth, i, buttonWidth);
            if (isInside(mouseX, mouseY, buttonLeft, BUTTON_TOP, buttonWidth, BUTTON_HEIGHT)) {
                event.setCanceled(true);
                handle(i);
                return;
            }
        }
    }

    private static void handle(int button) {
        switch (button) {
            case 0 -> openDownloadPage();
            case 1 -> postpone();
            default -> ignoreThisVersion();
        }
    }

    private static void openDownloadPage() {
        try {
            Util.getPlatform().openUri(new URI(UpdateChecker.PROJECT_URL));
        } catch (URISyntaxException ignored) {
            // The URL is a compile-time constant; nothing sensible to recover to.
        }
        pending = null;
    }

    /** Hides the panel for three days; the same version is announced again after that. */
    private static void postpone() {
        AE2TextureUpdater.CONFIG.general.snoozeUntil = System.currentTimeMillis() + SNOOZE_MILLIS;
        AE2TextureUpdater.saveConfig();
        pending = null;
    }

    /** Never announce this version again; a newer release is still announced. */
    private static void ignoreThisVersion() {
        UpdateChecker.Result result = pending;
        if (result != null) {
            AE2TextureUpdater.CONFIG.general.ignoredVersion = result.latestVersion();
            AE2TextureUpdater.saveConfig();
        }
        pending = null;
    }

    private static int buttonWidth() {
        return (PANEL_WIDTH - 6 - BUTTON_SPACING * 2) / 3;
    }

    private static int buttonLeft(int guiWidth, int index, int buttonWidth) {
        int total = buttonWidth * 3 + BUTTON_SPACING * 2;
        int start = guiWidth - PANEL_WIDTH + (PANEL_WIDTH - total) / 2;
        return start + index * (buttonWidth + BUTTON_SPACING);
    }

    private static double mouseX(Window window) {
        return Minecraft.getInstance().mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth();
    }

    private static double mouseY(Window window) {
        return Minecraft.getInstance().mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight();
    }

    private static boolean isInside(double mouseX, double mouseY, int left, int top, int width, int height) {
        return mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height;
    }
}
