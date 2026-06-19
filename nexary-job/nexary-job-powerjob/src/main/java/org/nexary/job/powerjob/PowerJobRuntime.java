package org.nexary.job.powerjob;

import java.util.logging.Level;
import java.util.logging.Logger;

/** Internal logging hook kept separate from the public Job API. */
final class PowerJobRuntime {
    private static final Logger LOGGER = Logger.getLogger(PowerJobRuntime.class.getName());

    private PowerJobRuntime() {
    }

    static void log(String message) {
        if (message != null && !message.trim().isEmpty()) {
            LOGGER.log(Level.FINE, message);
        }
    }
}
