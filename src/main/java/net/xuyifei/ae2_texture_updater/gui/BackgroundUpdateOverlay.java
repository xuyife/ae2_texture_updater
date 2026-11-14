package net.xuyifei.ae2_texture_updater.gui;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.ChatFormatting;
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
import net.xuyifei.ae2_texture_updater.update.BackgroundUpdateState;
import net.xuyifei.ae2_texture_updater.update.ResourcePackPrompt;

/**
 * Progress panel for a background update run, pinned to the top-right corner.
 *
 * <p>Same shape as {@link UpdateAvailableOverlay}: it is drawn over whatever is open
 * and only swallows clicks that land on its own buttons, so the player keeps playing.
 * Two buttons: <em>Cancel</em> stops the run and clears the dynamic cache, <em>Hide</em>
 * only closes the panel — the run keeps going and still reports its result by toast.</p>
 */
@Mod.EventBusSubscriber(modid = AE2TextureUpdater.MODID, value = Dist.CLIENT)
public final class BackgroundUpdateOverlay {

    private static final int PANEL_WIDTH = 160;
    private static final int PANEL_HEIGHT = 52;
    private static final int MARGIN = 4;
    private static final int PADDING = 4;
    private static final int TITLE_TOP = 4;
    private static final int STEP_TOP = 15;
    private static final int BAR_TOP = 27;
    private static final int BAR_HEIGHT = 4;
    private static final int BUTTON_TOP = 35;
    private static final int BUTTON_HEIGHT = 12;
    private static final int BUTTON_WIDTH = 74;
    private static final int BUTTON_SPACING = 4;

    private static final int COLOUR_PANEL = 0xC0101010;
    private static final int COLOUR_BAR_BACK = 0xFF202020;
    private static final int COLOUR_BAR_FILL = 0xFF55FF55;
    private static final int COLOUR_BUTTON = 0xFF2A2A2A;
    private static final int COLOUR_BUTTON_HOVER = 0xFF3F6F3F;
    private static final int COLOUR_TITLE = 0x55FF55;
    private static final int COLOUR_TEXT = 0xFFFFFF;

    private BackgroundUpdateOverlay() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null) {
            return;
        }
        Window window = minecraft.getWindow();
        if (minecraft.screen instanceof ResourcePackPromptScreen) {
            // The modal question owns the screen; the corner panel would only add noise.
            return;
        }
        if (!BackgroundUpdateState.visible() && !ResourcePackPrompt.forcesPanelVisible()) {
            return;
        }
        draw(event.getGuiGraphics(), window.getGuiScaledWidth(), mouseX(window), mouseY(window), minecraft);
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!BackgroundUpdateState.visible() && !ResourcePackPrompt.forcesPanelVisible()) {
            return;
        }
        draw(event.getGuiGraphics(), event.getScreen().width,
                event.getMouseX(), event.getMouseY(), Minecraft.getInstance());
    }

    private static void draw(GuiGraphics graphics, int guiWidth, double mouseX, double mouseY,
                             Minecraft minecraft) {
        int left = guiWidth - PANEL_WIDTH - MARGIN;
        int top = MARGIN;

        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, COLOUR_PANEL);
        graphics.drawString(minecraft.font, Component.literal("AE2 Texture Updater"),
                left + PADDING, top + TITLE_TOP, COLOUR_TITLE);
        Component stepLine = ResourcePackPrompt.isSnoozing()
                ? Component.translatable("overlay.ae2_texture_updater.reload_prompt.snoozed",
                        ResourcePackPrompt.snoozeSecondsLeft())
                : Component.translatable("overlay.ae2_texture_updater.background.step",
                        BackgroundUpdateState.stepLabel());
        graphics.drawString(minecraft.font, stepLine.copy().withStyle(ChatFormatting.GRAY),
                left + PADDING, top + STEP_TOP, COLOUR_TEXT);

        int barLeft = left + PADDING;
        int barWidth = PANEL_WIDTH - PADDING * 2;
        graphics.fill(barLeft, top + BAR_TOP, barLeft + barWidth, top + BAR_TOP + BAR_HEIGHT, COLOUR_BAR_BACK);
        int filled = barWidth * Math.max(0, Math.min(100, BackgroundUpdateState.percent())) / 100;
        graphics.fill(barLeft, top + BAR_TOP, barLeft + filled, top + BAR_TOP + BAR_HEIGHT, COLOUR_BAR_FILL);

        String[] labels = {
                "overlay.ae2_texture_updater.background.button.cancel",
                "overlay.ae2_texture_updater.background.button.hide"
        };
        for (int i = 0; i < labels.length; i++) {
            int buttonLeft = buttonLeft(guiWidth, i);
            int buttonTop = top + BUTTON_TOP;
            boolean hovered = isInside(mouseX, mouseY, buttonLeft, buttonTop, BUTTON_WIDTH, BUTTON_HEIGHT);
            graphics.fill(buttonLeft, buttonTop, buttonLeft + BUTTON_WIDTH, buttonTop + BUTTON_HEIGHT,
                    hovered ? COLOUR_BUTTON_HOVER : COLOUR_BUTTON);
            graphics.drawCenteredString(minecraft.font,
                    Component.translatable(labels[i]).withStyle(ChatFormatting.WHITE),
                    buttonLeft + BUTTON_WIDTH / 2, buttonTop + 2, COLOUR_TEXT);
        }
    }

    @SubscribeEvent
    public static void onMousePressed(InputEvent.MouseButton.Pre event) {
        if (event.getButton() != 0) {
            return;
        }
        Window window = Minecraft.getInstance().getWindow();
        int guiWidth = window.getGuiScaledWidth();
        double mouseX = mouseX(window);
        double mouseY = mouseY(window);

        if (!BackgroundUpdateState.visible()) {
            return;
        }
        for (int i = 0; i < 2; i++) {
            int buttonLeft = buttonLeft(guiWidth, i);
            if (isInside(mouseX, mouseY, buttonLeft, MARGIN + BUTTON_TOP, BUTTON_WIDTH, BUTTON_HEIGHT)) {
                event.setCanceled(true);
                if (i == 0) {
                    BackgroundUpdateState.cancel();
                } else {
                    BackgroundUpdateState.setHidden(true);
                }
                return;
            }
        }
    }

    private static int buttonLeft(int guiWidth, int index) {
        int total = BUTTON_WIDTH * 2 + BUTTON_SPACING;
        int start = guiWidth - PANEL_WIDTH - MARGIN + (PANEL_WIDTH - total) / 2;
        return start + index * (BUTTON_WIDTH + BUTTON_SPACING);
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