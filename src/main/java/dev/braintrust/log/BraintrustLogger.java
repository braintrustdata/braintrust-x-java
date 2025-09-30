package dev.braintrust.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized logging for Braintrust SDK. Provides consistent logging with SDK-specific control and
 * configuration.
 */
public final class BraintrustLogger {
    private static final String LOGGER_NAME = "braintrust";

    /**
     * Get or create the braintrust logger
     *
     * <p>Note: this calls LoggerFactory which may initialize a global logger. Set your desired
     * logging globals before calling this method.
     */
    public static Logger get() {
        return LoggerFactory.getLogger(LOGGER_NAME);
    }
}
