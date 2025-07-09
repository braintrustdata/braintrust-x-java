package dev.braintrust.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * Centralized logging for Braintrust SDK.
 * Provides consistent logging with SDK-specific control and configuration.
 */
public final class BraintrustLogger {
    private static final String LOGGER_NAME = "braintrust";
    private static final String DEBUG_ENV = "BRAINTRUST_DEBUG";
    
    private static final ReadWriteLock lock = new ReentrantReadWriteLock();
    private static LoggerImplementation implementation;
    private static Level minLevel;
    
    static {
        // Initialize with default SLF4J implementation
        var defaultLogger = LoggerFactory.getLogger(LOGGER_NAME);
        implementation = new Slf4jLoggerImplementation(defaultLogger);
        
        // Check environment for debug mode
        var debugEnabled = Boolean.parseBoolean(System.getenv(DEBUG_ENV));
        minLevel = debugEnabled ? Level.DEBUG : Level.INFO;
    }
    
    private BraintrustLogger() {
        // Utility class
    }
    
    /**
     * Sets a custom logger implementation.
     */
    public static void setLogger(LoggerImplementation logger) {
        lock.writeLock().lock();
        try {
            implementation = logger;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Sets the minimum log level.
     */
    public static void setLevel(Level level) {
        lock.writeLock().lock();
        try {
            minLevel = level;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Enables or disables debug logging.
     */
    public static void setDebugEnabled(boolean enabled) {
        setLevel(enabled ? Level.DEBUG : Level.INFO);
    }
    
    /**
     * Logs a debug message.
     */
    public static void debug(String message, Object... args) {
        log(Level.DEBUG, message, args);
    }
    
    /**
     * Logs a debug message with lazy evaluation.
     */
    public static void debug(Supplier<String> messageSupplier) {
        if (isEnabled(Level.DEBUG)) {
            log(Level.DEBUG, messageSupplier.get());
        }
    }
    
    /**
     * Logs an info message.
     */
    public static void info(String message, Object... args) {
        log(Level.INFO, message, args);
    }
    
    /**
     * Logs a warning message.
     */
    public static void warn(String message, Object... args) {
        log(Level.WARN, message, args);
    }
    
    /**
     * Logs a warning message with an exception.
     */
    public static void warn(String message, Throwable throwable, Object... args) {
        lock.readLock().lock();
        try {
            if (isEnabled(Level.WARN)) {
                implementation.log(Level.WARN, message, throwable, args);
            }
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Logs an error message.
     */
    public static void error(String message, Object... args) {
        log(Level.ERROR, message, args);
    }
    
    /**
     * Logs an error message with an exception.
     */
    public static void error(String message, Throwable throwable, Object... args) {
        lock.readLock().lock();
        try {
            if (isEnabled(Level.ERROR)) {
                implementation.log(Level.ERROR, message, throwable, args);
            }
        } finally {
            lock.readLock().unlock();
        }
    }
    
    private static void log(Level level, String message, Object... args) {
        lock.readLock().lock();
        try {
            if (isEnabled(level)) {
                implementation.log(level, message, null, args);
            }
        } finally {
            lock.readLock().unlock();
        }
    }
    
    private static boolean isEnabled(Level level) {
        return level.compareTo(minLevel) >= 0;
    }
    
    /**
     * Logger implementation interface for pluggable logging.
     */
    public interface LoggerImplementation {
        void log(Level level, String message, Throwable throwable, Object... args);
    }
    
    /**
     * Default SLF4J-based implementation.
     */
    private static class Slf4jLoggerImplementation implements LoggerImplementation {
        private final Logger logger;
        
        Slf4jLoggerImplementation(Logger logger) {
            this.logger = logger;
        }
        
        @Override
        public void log(Level level, String message, Throwable throwable, Object... args) {
            // Format message with prefix
            var prefixedMessage = "braintrust: " + message;
            
            switch (level) {
                case DEBUG -> {
                    if (throwable != null) {
                        logger.debug(prefixedMessage, args, throwable);
                    } else {
                        logger.debug(prefixedMessage, args);
                    }
                }
                case INFO -> {
                    if (throwable != null) {
                        logger.info(prefixedMessage, args, throwable);
                    } else {
                        logger.info(prefixedMessage, args);
                    }
                }
                case WARN -> {
                    if (throwable != null) {
                        logger.warn(prefixedMessage, args, throwable);
                    } else {
                        logger.warn(prefixedMessage, args);
                    }
                }
                case ERROR -> {
                    if (throwable != null) {
                        logger.error(prefixedMessage, args, throwable);
                    } else {
                        logger.error(prefixedMessage, args);
                    }
                }
            }
        }
    }
    
    /**
     * Test implementation that captures log messages.
     */
    public static class TestLoggerImplementation implements LoggerImplementation {
        private final java.util.List<LogEntry> entries = new java.util.ArrayList<>();
        
        @Override
        public void log(Level level, String message, Throwable throwable, Object... args) {
            entries.add(new LogEntry(level, String.format(message, args), throwable));
        }
        
        public java.util.List<LogEntry> getEntries() {
            return new java.util.ArrayList<>(entries);
        }
        
        public void clear() {
            entries.clear();
        }
        
        public record LogEntry(Level level, String message, Throwable throwable) {}
    }
}