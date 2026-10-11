package net.xuyifei.ae2_texture_updater.update;

import net.minecraftforge.fml.loading.progress.ProgressMeter;

/**
 * Thin facade over an FML {@link ProgressMeter} so steps can report percent
 * progress (0-100) without knowing the meter's internal step scale.
 * The scale guard avoids a NaN progress value when a meter was created with 0 steps.
 */
public final class UpdateProgress {
    private final ProgressMeter meter;
    private final int scale;

    public UpdateProgress(ProgressMeter meter, int scale) {
        this.meter = meter;
        this.scale = Math.max(1, scale);
    }

    public void setPercent(long percent) {
        if (meter != null) {
            meter.setAbsolute((int) (percent * scale / 100));
        }
    }

    public void complete() {
        if (meter != null) {
            meter.complete();
        }
    }
}

