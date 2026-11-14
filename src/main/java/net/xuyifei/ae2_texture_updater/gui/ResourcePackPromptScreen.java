package net.xuyifei.ae2_texture_updater.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.xuyifei.ae2_texture_updater.update.ResourcePackPrompt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Full-screen confirmation used when the player is <em>not</em> in a world.
 *
 * <p>This is a real {@link Screen}, unlike the corner panel, because nothing is being
 * interrupted here: the player is on a menu, so a normal modal dialogue is the clearest thing
 * to show. While playing, {@link ResourcePackPrompt} uses chat instead.</p>
 */
@OnlyIn(Dist.CLIENT)
public class ResourcePackPromptScreen extends Screen {

    private static final int BUTTON_WIDTH = 100;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 4;

    /** Screen to return to once the player answers; may be {@code null}. */
    @Nullable
    private final Screen parent;

    public ResourcePackPromptScreen(@Nullable Screen parent) {
        super(Component.translatable("screen.ae2_texture_updater.reload_prompt.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int total = BUTTON_WIDTH * 3 + BUTTON_SPACING * 2;
        int startX = (this.width - total) / 2;
        int y = this.height / 2 + 20;

        this.addRenderableWidget(Button.builder(
                        Component.translatable("overlay.ae2_texture_updater.reload_prompt.button.continue"),
                        button -> answer(ResourcePackPrompt.Decision.CONTINUE))
                .bounds(startX, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());

        this.addRenderableWidget(Button.builder(
                        Component.translatable("overlay.ae2_texture_updater.reload_prompt.button.later"),
                        button -> answer(ResourcePackPrompt.Decision.LATER))
                .bounds(startX + BUTTON_WIDTH + BUTTON_SPACING, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());

        this.addRenderableWidget(Button.builder(
                        Component.translatable("overlay.ae2_texture_updater.reload_prompt.button.cancel"),
                        button -> answer(ResourcePackPrompt.Decision.CANCEL))
                .bounds(startX + (BUTTON_WIDTH + BUTTON_SPACING) * 2, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    private void answer(ResourcePackPrompt.Decision decision) {
        ResourcePackPrompt.answer(decision);
        closeToParent();
    }

    private void closeToParent() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // A real screen with the vanilla menu backdrop, so it reads as a modal dialogue rather
        // than a floating panel.
        this.renderBackground(graphics);

        int centerX = this.width / 2;
        int lineY = this.height / 2 - 40;
        graphics.drawCenteredString(this.font, this.title, centerX, lineY, 0xFF55FF);
        graphics.drawCenteredString(this.font,
                Component.translatable("overlay.ae2_texture_updater.reload_prompt.line1"),
                centerX, lineY + 20, 0xFFFFFF);
        graphics.drawCenteredString(this.font,
                Component.translatable("overlay.ae2_texture_updater.reload_prompt.line2"),
                centerX, lineY + 32, 0xFFFFFF);
        graphics.drawCenteredString(this.font,
                Component.translatable("overlay.ae2_texture_updater.reload_prompt.line3"),
                centerX, lineY + 44, 0xFFFFFF);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /**
     * Esc must not answer the question: closing the screen would leave the update thread
     * waiting forever. It behaves like "later" instead, which keeps the prompt alive.
     */
    @Override
    public void onClose() {
        ResourcePackPrompt.answer(ResourcePackPrompt.Decision.LATER);
        closeToParent();
    }

    /** Answering through a button already closes the screen; this keeps them from stacking. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
