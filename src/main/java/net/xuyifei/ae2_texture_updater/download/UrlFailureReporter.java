package net.xuyifei.ae2_texture_updater.download;

import net.xuyifei.ae2_texture_updater.AE2TextureUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central place where URL resolution failures are logged; keeps the
 * fetch methods free of logging concerns and avoids duplicate log lines.
 */
final class UrlFailureReporter {
    private static final Logger LOGGER = LoggerFactory.getLogger("AE2TextureUpdater");

    private UrlFailureReporter() {
    }

    static void report(String reason) {
        LOGGER.warn("Download URL resolution failed: {}", reason);
    }
}
