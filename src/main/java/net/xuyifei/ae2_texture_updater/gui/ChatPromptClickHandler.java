package net.xuyifei.ae2_texture_updater.gui;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Style;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.xuyifei.ae2_texture_updater.AE2TextureUpdater;
import net.xuyifei.ae2_texture_updater.update.ResourcePackPrompt;

import java.util.Locale;

/**
 * Handles clicks on the chat question's buttons.
 *
 * <p>Vanilla cannot dispatch them: a click value without arguments is routed to
 * {@code ClientPacketListener.sendUnsignedCommand}, which sends it straight to the server and
 * never consults Forge's {@code ClientCommandHandler} (only {@code sendCommand} does), so a
 * client-only command always comes back as "unknown command". Clicking is therefore handled
 * here, before vanilla sees the mouse press, and the event is cancelled so nothing is sent.</p>
 *
 * <p>Any click on one of our buttons is swallowed, not only the one that answers the live
 * question. The line stays in the chat history, so clicking it again after answering would
 * otherwise fall through to vanilla and produce an "unknown command" reply. A stale click is
 * swallowed and ignored: the token no longer matches the active round.</p>
 */
@Mod.EventBusSubscriber(modid = AE2TextureUpdater.MODID, value = Dist.CLIENT)
public final class ChatPromptClickHandler {

    /** Prefix carried in the click event so a click can be recognised without parsing chat text. */
    public static final String CLICK_PREFIX = "/ae2tu_reload ";

    private ChatPromptClickHandler() {
    }

    @SubscribeEvent
    public static void onMousePressed(InputEvent.MouseButton.Pre event) {
        if (event.getButton() != 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof ChatScreen)) {
            return;
        }

        Window window = minecraft.getWindow();
        double mouseX = minecraft.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth();
        double mouseY = minecraft.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight();
        Style style = minecraft.gui.getChat().getClickedComponentStyleAt(mouseX, mouseY);
        if (style == null || style.getClickEvent() == null) {
            return;
        }
        String value = style.getClickEvent().getValue();
        if (!value.startsWith(CLICK_PREFIX)) {
            return;
        }

        // Ours: always swallow the press, even when the question is already answered or the
        // token is stale, so vanilla never forwards it to the server as an unknown command.
        event.setCanceled(true);
        if (!ResourcePackPrompt.isWaiting()) {
            return;
        }
        String token = ResourcePackPrompt.activeTokenAsString();
        if (token == null || !value.endsWith(" " + token)) {
            return;
        }

        // Command values are "<prefix><action> <token>"; the action is the middle word.
        String action = value.substring(CLICK_PREFIX.length(), value.length() - token.length() - 1).trim();
        ResourcePackPrompt.Decision decision = parse(action);
        if (decision != null) {
            ResourcePackPrompt.answer(decision);
        }
    }

    private static ResourcePackPrompt.Decision parse(String action) {
        for (ResourcePackPrompt.Decision decision : ResourcePackPrompt.Decision.values()) {
            if (decision.name().toLowerCase(Locale.ROOT).equals(action)) {
                return decision;
            }
        }
        return null;
    }
}
