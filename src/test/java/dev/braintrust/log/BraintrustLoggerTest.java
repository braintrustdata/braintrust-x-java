package dev.braintrust.log;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.event.Level;

import static org.assertj.core.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
@Execution(ExecutionMode.SAME_THREAD)
class BraintrustLoggerTest {
    
    private BraintrustLogger.TestLoggerImplementation testLogger;
    private BraintrustLogger.LoggerImplementation savedLogger;
    private Level savedLevel;
    
    @BeforeEach
    void setUp() {
        // Save current state
        savedLogger = BraintrustLogger.getLogger();
        savedLevel = BraintrustLogger.getLevel();
        
        // Set test logger
        testLogger = new BraintrustLogger.TestLoggerImplementation();
        BraintrustLogger.setLogger(testLogger);
        // Reset to default INFO level
        BraintrustLogger.setLevel(Level.INFO);
        // Clear any existing entries
        testLogger.clear();
    }
    
    @AfterEach
    void tearDown() {
        // Restore original state
        BraintrustLogger.setLogger(savedLogger);
        BraintrustLogger.setLevel(savedLevel);
    }
    
    @Test
    void testDebugLogging() {
        // Given
        BraintrustLogger.setDebugEnabled(true);
        
        // When
        BraintrustLogger.debug("Debug message with {}", "parameter");
        
        // Then
        assertThat(testLogger.getEntries()).hasSize(1);
        var entry = testLogger.getEntries().get(0);
        assertThat(entry.level()).isEqualTo(Level.DEBUG);
        assertThat(entry.message()).isEqualTo("Debug message with parameter");
        assertThat(entry.throwable()).isNull();
    }
    
    @Test
    void testDebugLoggingDisabled() {
        // Given
        BraintrustLogger.setDebugEnabled(false);
        
        // When
        BraintrustLogger.debug("Debug message");
        
        // Then
        assertThat(testLogger.getEntries()).isEmpty();
    }
    
    @Test
    void testDebugLoggingWithSupplier() {
        // Given
        BraintrustLogger.setDebugEnabled(true);
        var supplierCalled = new boolean[]{false};
        
        // When
        BraintrustLogger.debug(() -> {
            supplierCalled[0] = true;
            return "Expensive debug message";
        });
        
        // Then
        assertThat(supplierCalled[0]).isTrue();
        assertThat(testLogger.getEntries()).hasSize(1);
        assertThat(testLogger.getEntries().get(0).message()).isEqualTo("Expensive debug message");
    }
    
    @Test
    void testDebugSupplierNotCalledWhenDisabled() {
        // Given
        BraintrustLogger.setDebugEnabled(false);
        var supplierCalled = new boolean[]{false};
        
        // When
        BraintrustLogger.debug(() -> {
            supplierCalled[0] = true;
            return "Expensive debug message";
        });
        
        // Then
        assertThat(supplierCalled[0]).isFalse();
        assertThat(testLogger.getEntries()).isEmpty();
    }
    
    @Test
    void testInfoLogging() {
        // When
        BraintrustLogger.info("Info message: {}", "test");
        
        // Then
        assertThat(testLogger.getEntries()).hasSize(1);
        var entry = testLogger.getEntries().get(0);
        assertThat(entry.level()).isEqualTo(Level.INFO);
        assertThat(entry.message()).isEqualTo("Info message: test");
    }
    
    @Test
    void testWarnLogging() {
        // When
        BraintrustLogger.warn("Warning: {}", "something");
        
        // Then
        assertThat(testLogger.getEntries()).hasSize(1);
        var entry = testLogger.getEntries().get(0);
        assertThat(entry.level()).isEqualTo(Level.WARN);
        assertThat(entry.message()).isEqualTo("Warning: something");
    }
    
    @Test
    void testWarnLoggingWithThrowable() {
        // Given
        var exception = new RuntimeException("Test exception");
        
        // When
        BraintrustLogger.warn("Warning with exception", exception);
        
        // Then
        assertThat(testLogger.getEntries()).hasSize(1);
        var entry = testLogger.getEntries().get(0);
        assertThat(entry.level()).isEqualTo(Level.WARN);
        assertThat(entry.message()).isEqualTo("Warning with exception");
        assertThat(entry.throwable()).isEqualTo(exception);
    }
    
    @Test
    void testErrorLogging() {
        // When
        BraintrustLogger.error("Error: {}", "critical");
        
        // Then
        assertThat(testLogger.getEntries()).hasSize(1);
        var entry = testLogger.getEntries().get(0);
        assertThat(entry.level()).isEqualTo(Level.ERROR);
        assertThat(entry.message()).isEqualTo("Error: critical");
    }
    
    @Test
    void testErrorLoggingWithThrowable() {
        // Given
        var exception = new IllegalStateException("Test error");
        
        // When
        BraintrustLogger.error("Error occurred", exception);
        
        // Then
        assertThat(testLogger.getEntries()).hasSize(1);
        var entry = testLogger.getEntries().get(0);
        assertThat(entry.level()).isEqualTo(Level.ERROR);
        assertThat(entry.message()).isEqualTo("Error occurred");
        assertThat(entry.throwable()).isEqualTo(exception);
    }
    
    @Test
    void testLevelFiltering() {
        // Given
        BraintrustLogger.setLevel(Level.WARN);
        
        // When
        BraintrustLogger.debug("Debug");
        BraintrustLogger.info("Info");
        BraintrustLogger.warn("Warn");
        BraintrustLogger.error("Error");
        
        // Then
        assertThat(testLogger.getEntries()).hasSize(2);
        assertThat(testLogger.getEntries())
            .extracting(BraintrustLogger.TestLoggerImplementation.LogEntry::level)
            .containsExactly(Level.WARN, Level.ERROR);
    }
    
    @Test
    void testMessageFormatting() {
        // When
        BraintrustLogger.info("Message with {} {} {}", "multiple", "parameters", 123);
        
        // Then
        assertThat(testLogger.getEntries()).hasSize(1);
        assertThat(testLogger.getEntries().get(0).message())
            .isEqualTo("Message with multiple parameters 123");
    }
    
    @Test
    void testClearLogEntries() {
        // Given
        BraintrustLogger.info("Message 1");
        BraintrustLogger.info("Message 2");
        assertThat(testLogger.getEntries()).hasSize(2);
        
        // When
        testLogger.clear();
        
        // Then
        assertThat(testLogger.getEntries()).isEmpty();
    }
    
    @Test
    void testThreadSafety() throws InterruptedException {
        // Given
        var threads = new Thread[10];
        var messagesPerThread = 100;
        
        // When
        for (int i = 0; i < threads.length; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < messagesPerThread; j++) {
                    BraintrustLogger.info("Thread {} message {}", threadId, j);
                }
            });
            threads[i].start();
        }
        
        // Wait for all threads
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Then
        assertThat(testLogger.getEntries()).hasSize(threads.length * messagesPerThread);
    }
}