package net.xuyifei.ae2_texture_updater.update;


import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mutable view of a background run, read by the in-game panel and written by the
 * pipeline thread. Every field is volatile or atomic: the render thread only reads.
 */
public final class BackgroundUpdateState {

    /** What the panel is currently showing. */
    public enum Status { IDLE, RUNNING, FINISHED }

    private static volatile Status status = Status.IDLE;
    private static volatile String stepLabel = "";
    private static volatile int percent;
    private static volatile boolean hidden;
    private static final AtomicBoolean cancelled = new AtomicBoolean(false);

    private BackgroundUpdateState() {
    }

    public static Status status() {
        return status;
    }

    public static String stepLabel() {
        return stepLabel;
    }

    public static int percent() {
        return percent;
    }

    public static boolean hidden() {
        return hidden;
    }

    public static void setHidden(boolean value) {
        hidden = value;
    }

    public static boolean isCancelled() {
        return cancelled.get();
    }

    /** Whether the panel should be drawn: running and not hidden by the player. */
    public static boolean visible() {
        return status == Status.RUNNING && !hidden;
    }

    /** Starts a run: clears the cancel flag and any previous hidden state. */
    public static void begin() {
        cancelled.set(false);
        hidden = false;
        percent = 0;
        stepLabel = "";
        status = Status.RUNNING;
    }

    public static void cancel() {
        cancelled.set(true);
        // Wake a thread parked in ResourcePackPrompt.askAndWait(), otherwise cancelling
        // while the confirmation is up would leave the update thread blocked forever.
        ResourcePackPrompt.abort();
    }

    public static void finish() {
        status = Status.FINISHED;
        percent = 0;
        stepLabel = "";
        // No run is in flight any more, so a parked confirmation must not survive it.
        ResourcePackPrompt.reset();
    }

    static void update(String label, int value) {
        stepLabel = label;
        percent = value;
    }

    /** Panel/state pair reset on a fresh session; also used by tests. */
    public static void reset() {
        status = Status.IDLE;
        stepLabel = "";
        percent = 0;
        hidden = false;
        cancelled.set(false);
    }

    /** Reporter that writes into this state and honours {@link #cancel()}. */
    public static final class Reporter implements UpdateReporter {

        @Override
        public Stage stage(String label, int scale) {
            return new Stage() {
                private boolean done;

                @Override
                public void percent(int value) {
                    if (!done) {
                        update(label, Math.max(0, Math.min(100, value)));
                    }
                }

                @Override
                public void complete() {
                    if (!done) {
                        done = true;
                        update(label, 100);
                    }
                }
            };
        }

        @Override
        public boolean isCancelled() {
            return BackgroundUpdateState.isCancelled();
        }
    }
}