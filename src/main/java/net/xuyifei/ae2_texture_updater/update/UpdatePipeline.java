package net.xuyifei.ae2_texture_updater.update;

import net.xuyifei.ae2_texture_updater.message.MessageKey;
import net.xuyifei.ae2_texture_updater.message.UserMessageManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs update steps in order and stops at the first failing step.
 * Each step reports its own progress and user messages.
 */
public final class UpdatePipeline {
    private static final Logger LOGGER = LoggerFactory.getLogger("AE2TextureUpdater");

    private boolean aborted = false;

    /**
     * Executes {@code step}; when it reports failure (or throws) the pipeline
     * records the failure key and skips all later steps.
     *
     * <p>{@link UpdateCancelledException} is not a failure: it propagates so the
     * caller can clear the cache, and it never writes a user-visible error.</p>
     */
    public boolean run(MessageKey failureKey, BooleanSupplier step) {
        if (aborted) {
            return false;
        }
        try {
            boolean ok = step.getAsBoolean();
            if (!ok) {
                aborted = true;
            }
            return ok;
        } catch (UpdateCancelledException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("Update step failed: {}", e.getMessage(), e);
            UserMessageManager.error(MessageKey.UNKNOWN_ERROR, e.getMessage());
            UserMessageManager.error(failureKey);
            aborted = true;
            return false;
        }
    }

    /** Whether any step failed, so the caller can report a failure instead of a success. */
    public boolean failed() {
        return aborted;
    }

    /** A step that either succeeds, fails with its own user message, or throws. */
    @FunctionalInterface
    public interface BooleanSupplier {
        boolean getAsBoolean();
    }
}
