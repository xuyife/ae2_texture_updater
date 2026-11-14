package net.xuyifei.ae2_texture_updater.update;

/**
 * Thin facade over a {@link UpdateReporter.Stage} so steps can report percent
 * progress (0-100) without knowing how the stage is rendered.
 */
public final class UpdateProgress {
    private final UpdateReporter.Stage stage;

    public UpdateProgress(UpdateReporter.Stage stage) {
        this.stage = stage;
    }

    public void setPercent(long percent) {
        if (stage != null) {
            stage.percent((int) Math.max(0, Math.min(100, percent)));
        }
    }

    public void complete() {
        if (stage != null) {
            stage.complete();
        }
    }
}
