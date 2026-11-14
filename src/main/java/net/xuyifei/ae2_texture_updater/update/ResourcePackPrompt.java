package net.xuyifei.ae2_texture_updater.update;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.xuyifei.ae2_texture_updater.AE2TextureUpdater;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Blocks the background update before the resource pack is unloaded and reloaded, asking the
 * player to allow it.
 *
 * <p>How the question is presented depends on what the player is doing: in the world it is
 * sent to chat as clickable text, so nothing is forced on top of gameplay; anywhere else (main
 * menu, other screens) it does not matter, and the overlay draws a normal background screen.
 * "Later" asks again after {@link #SNOOZE_MILLIS}; "cancel" aborts the whole run.</p>
 */
public final class ResourcePackPrompt {

    /** How long "later" keeps the question away. */
    private static final long SNOOZE_MILLIS = 5L * 60L * 1000L;
    /** Slice for the snooze sleep, so cancellation stays responsive. */
    private static final long SNOOZE_SLICE_MILLIS = 500L;
    /** How often the chat question is repeated while unanswered. */
    private static final long RESEND_INTERVAL_MILLIS = 5L * 60L * 1000L;

    public enum Decision { CONTINUE, LATER, CANCEL }

    private static final Object LOCK = new Object();

    private static volatile boolean waiting;
    private static volatile boolean snoozing;
    private static volatile long snoozeRemainingMillis;
    /** Which presentation the current round uses; decided by the game state when asking. */
    private static volatile Mode mode = Mode.SCREEN;
    /** Only the newest question accepts clicks, so stale chat lines cannot answer a later round. */
    private static final AtomicReference<UUID> activeToken = new AtomicReference<>();
    /** Guarded by {@link #LOCK}: the answer for the current round, or {@code null}. */
    private static Decision decision;
    /** Guarded by {@link #LOCK}: set when the run was cancelled elsewhere. */
    private static boolean aborted;

    /** How the question is shown: a chat line while in a world, a screen otherwise. */
    public enum Mode { SCREEN, CHAT }

    private ResourcePackPrompt() {
    }

    public static boolean isWaiting() {
        return waiting;
    }

    public static boolean isSnoozing() {
        return snoozing;
    }

    public static long snoozeSecondsLeft() {
        return Math.max(0L, (snoozeRemainingMillis + 999L) / 1000L);
    }

    /** Whether the panel must be shown even if the player hid it earlier. */
    public static boolean forcesPanelVisible() {
        return waiting || snoozing;
    }

    /** True when the current round is a chat question, so no screen may be drawn. */
    public static boolean usesChat() {
        return mode == Mode.CHAT;
    }

    /** Token of the question currently accepting clicks, or {@code null}. */
    public static UUID activeToken() {
        return activeToken.get();
    }

    /** Same token as text, so the click handler can match a chat button without allocating. */
    public static String activeTokenAsString() {
        UUID token = activeToken.get();
        return token == null ? null : token.toString();
    }

    /**
     * Asks the player and blocks until they pick "continue" or "cancel".
     *
     * @throws UpdateCancelledException on "cancel" (or when the run was cancelled elsewhere)
     */
    public static void askAndWait() {
        while (true) {
            Decision answered = askOnce();
            if (answered == Decision.CONTINUE) {
                // Explicitly the end of the wait: falling through to snooze() here made
                // "continue" behave like "later", which is the bug this replaces.
                return;
            }
            if (answered == Decision.CANCEL) {
                throw new UpdateCancelledException();
            }
            if (!snooze()) {
                throw new UpdateCancelledException();
            }
        }
    }

    private static Decision askOnce() {
        UUID token = UUID.randomUUID();
        synchronized (LOCK) {
            decision = null;
            aborted = false;
            waiting = true;
            activeToken.set(token);
        }
        // In a world the player must keep playing, so ask in chat; otherwise the overlay draws
        // a regular screen, which is also the only option while the main menu is up.
        Minecraft minecraft = Minecraft.getInstance();
        mode = minecraft.level != null ? Mode.CHAT : Mode.SCREEN;
        AE2TextureUpdater.LOGGER.info("Waiting for the player to allow the resource pack reload ({})", mode);
        if (mode == Mode.CHAT) {
            sendChatQuestion(token);
        } else {
            // Not in a world: show a real screen. Deferred to the client thread because this
            // method runs on the update thread.
            minecraft.execute(() -> {
                if (minecraft.screen == null || !(minecraft.screen instanceof net.xuyifei.ae2_texture_updater.gui.ResourcePackPromptScreen)) {
                    minecraft.setScreen(new net.xuyifei.ae2_texture_updater.gui.ResourcePackPromptScreen(minecraft.screen));
                }
            });
        }
        try {
            synchronized (LOCK) {
                long nextResend = System.currentTimeMillis() + RESEND_INTERVAL_MILLIS;
                while (decision == null && !aborted) {
                    long waitFor = Math.max(50L, nextResend - System.currentTimeMillis());
                    LOCK.wait(waitFor);
                    if (decision == null && !aborted && System.currentTimeMillis() >= nextResend) {
                        resendChatQuestion();
                        nextResend = System.currentTimeMillis() + RESEND_INTERVAL_MILLIS;
                    }
                }
                Decision answered = aborted ? Decision.CANCEL : decision;
                decision = null;
                waiting = false;
                activeToken.set(null);
                return answered;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            synchronized (LOCK) {
                waiting = false;
                activeToken.set(null);
            }
            throw new UpdateCancelledException();
        }
    }

    /** Posts the clickable question to chat. Scheduled on the client thread by the caller path. */
    private static void sendChatQuestion(UUID token) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            var player = minecraft.player;
            if (player == null) {
                // Left the world while asking: fall back to the screen presentation.
                mode = Mode.SCREEN;
                return;
            }
            player.displayClientMessage(Component.translatable(
                    "chat.ae2_texture_updater.reload_prompt.line").withStyle(ChatFormatting.GOLD), false);
            player.displayClientMessage(buildActions(token), false);
        });
    }

    /** Builds the "[ Continue ] [ Later ] [ Cancel ]" line, each part clickable. */
    private static Component buildActions(UUID token) {
        return Component.empty()
                .append(button(token, Decision.CONTINUE))
                .append(Component.literal(" "))
                .append(button(token, Decision.LATER))
                .append(Component.literal(" "))
                .append(button(token, Decision.CANCEL));
    }

    private static Component button(UUID token, Decision decision) {
        String key = "chat.ae2_texture_updater.reload_prompt.button."
                + decision.name().toLowerCase(java.util.Locale.ROOT);
        // The click is intercepted by ChatPromptClickHandler before vanilla dispatch, because
        // vanilla would forward an argument-less command straight to the server and never reach
        // Forge's client command handler. The value is still shaped like a command so the style
        // carries a stable, recognisable payload.
        String command = net.xuyifei.ae2_texture_updater.gui.ChatPromptClickHandler.CLICK_PREFIX
                + decision.name().toLowerCase(java.util.Locale.ROOT) + " " + token;
        return Component.translatable(key)
                .withStyle(style -> style
                        .withColor(ChatFormatting.AQUA)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("chat.ae2_texture_updater.reload_prompt.hover"))));
    }

    /**
     * Re-posts the question to chat while waiting for an answer.
     *
     * <p>The player may have missed the line: chat scrolls, and a world session can run for
     * hours. Repeating it every {@link #RESEND_INTERVAL_MILLIS} keeps the prompt discoverable
     * without ever forcing a screen on top of gameplay.</p>
     */
    private static void resendChatQuestion() {
        UUID token = activeToken.get();
        if (token != null && waiting && mode == Mode.CHAT) {
            sendChatQuestion(token);
        }
    }

    /**
     * Sleeps out the snooze window in slices; the chat question is re-sent on each slice end so
     * the player is reminded without any screen appearing.
     *
     * @return {@code false} when the run was cancelled or the thread was interrupted
     */
    private static boolean snooze() {
        snoozing = true;
        long deadline = System.currentTimeMillis() + SNOOZE_MILLIS;
        try {
            while (true) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0L) {
                    return true;
                }
                snoozeRemainingMillis = remaining;
                if (BackgroundUpdateState.isCancelled()) {
                    return false;
                }
                Thread.sleep(Math.min(SNOOZE_SLICE_MILLIS, remaining));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            snoozing = false;
            snoozeRemainingMillis = 0L;
        }
    }

    /** Called from the overlay (screen mode) when the player clicks a button. */
    public static void answer(Decision answered) {
        synchronized (LOCK) {
            decision = answered;
            LOCK.notifyAll();
        }
    }

    /** Called from the chat command handler; the token must match the current question. */
    public static boolean answer(UUID token, Decision answered) {
        if (token == null || !token.equals(activeToken.get())) {
            return false;
        }
        answer(answered);
        return true;
    }

    /** Releases a waiting thread when the run is cancelled elsewhere; safe to call anytime. */
    public static void abort() {
        synchronized (LOCK) {
            if (!waiting) {
                return;
            }
            aborted = true;
            LOCK.notifyAll();
        }
    }

    /** Clears the state after a run, so a later run starts from a clean slate. */
    public static void reset() {
        synchronized (LOCK) {
            waiting = false;
            snoozing = false;
            snoozeRemainingMillis = 0L;
            decision = null;
            aborted = false;
            activeToken.set(null);
            LOCK.notifyAll();
        }
    }
}