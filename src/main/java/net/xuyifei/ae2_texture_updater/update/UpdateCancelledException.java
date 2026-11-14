package net.xuyifei.ae2_texture_updater.update;

/**
 * Thrown from inside a step when the player cancelled the background run.
 *
 * <p>It is deliberately an unchecked, non-{@link java.io.IOException} type: the
 * pipeline treats it as "stopped on purpose" rather than as a failure, so a
 * cancellation never lands in the user log or the result toast.</p>
 */
public class UpdateCancelledException extends RuntimeException {
    public UpdateCancelledException() {
        super("update cancelled by the player");
    }
}
