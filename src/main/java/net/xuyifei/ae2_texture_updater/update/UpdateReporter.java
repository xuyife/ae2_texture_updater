package net.xuyifei.ae2_texture_updater.update;

import net.minecraftforge.fml.StartupMessageManager;
import net.minecraftforge.fml.loading.progress.ProgressMeter;

/**
 * Progress sink shared by both run modes, so the update steps never need to know
 * whether they are running under the FML loading screen or in the background.
 *
 * <p>Implementations also answer {@link #isCancelled()}: the steps poll it inside
 * their loops and abort by throwing {@link UpdateCancelledException}.</p>
 */
public interface UpdateReporter {

    /**
     * Opens a progress area for one step.
     *
     * @param label human readable step label (already carries the "x/5" prefix)
     * @param scale the value passed to {@link ProgressMeter#setAbsolute}, 100 for percent
     */
    Stage stage(String label, int scale);

    /** Whether the player asked to stop the current run. */
    boolean isCancelled();

    /** Throws when the run was cancelled, so steps can bail out of long loops. */
    default void checkCancelled() {
        if (isCancelled()) {
            throw new UpdateCancelledException();
        }
    }

    /** One step's progress area; mirrors the FML meter but is mode independent. */
    interface Stage {
        /** Reports a percentage (0-100) of this stage. */
        void percent(int percent);

        /** Finishes the stage; safe to call more than once. */
        void complete();
    }

    /** A {@link Stage} backed by an FML {@link ProgressMeter}, used during startup. */
    final class StartupStage implements Stage {
        private final ProgressMeter meter;
        private final int scale;
        private boolean done;

        StartupStage(ProgressMeter meter) {
            this.meter = meter;
            this.scale = Math.max(1, meter.steps());
        }

        @Override
        public void percent(int percent) {
            if (!done && meter != null) {
                meter.setAbsolute((int) ((long) percent * scale / 100));
            }
        }

        @Override
        public void complete() {
            if (!done) {
                done = true;
                if (meter != null) {
                    meter.complete();
                }
            }
        }
    }

    /** Reports onto the FML loading screen; the historical behaviour. */
    final class Startup implements UpdateReporter {
        @Override
        public Stage stage(String label, int scale) {
            return new StartupStage(StartupMessageManager.addProgressBar(label, scale));
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    }
}
