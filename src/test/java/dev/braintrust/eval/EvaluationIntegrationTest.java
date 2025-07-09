package dev.braintrust.eval;

import dev.braintrust.config.BraintrustConfig;
import dev.braintrust.trace.BraintrustContext;
import dev.braintrust.trace.BraintrustSpanProcessor;
import dev.braintrust.trace.BraintrustTracing;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;

class EvaluationIntegrationTest {
    
    private InMemorySpanExporter spanExporter;
    private BraintrustConfig config;
    
    @BeforeEach
    void setUp() {
        GlobalOpenTelemetry.resetForTest();
        spanExporter = InMemorySpanExporter.create();
        
        config = BraintrustConfig.builder()
            .apiKey("test-key")
            .defaultProjectId("test-project")
            .build();
        
        // Set up tracing with in-memory exporter
        var tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(new BraintrustSpanProcessor.Builder(config)
                .withExporter(spanExporter)
                .build())
            .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
            .build();
        
        GlobalOpenTelemetry.resetForTest();
        GlobalOpenTelemetry.set(io.opentelemetry.sdk.OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .build());
    }
    
    @AfterEach
    void tearDown() {
        GlobalOpenTelemetry.resetForTest();
    }
    
    @Test
    void testEvaluationWithTracing() {
        // Given
        var testData = List.of("test1", "test2", "test3");
        
        // When
        var results = Evaluation.<String, String>builder()
            .name("traced-evaluation")
            .data(testData)
            .task(String::toUpperCase)
            .scorer("length", (input, output) -> 
                output.length() == input.length() ? 1.0 : 0.0)
            .run();
        
        // Then
        assertThat(results.results()).hasSize(3);
        
        // Verify spans
        var spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSizeGreaterThan(0);
        
        // Find evaluation span
        var evalSpan = spans.stream()
            .filter(s -> s.getName().equals("evaluation"))
            .findFirst()
            .orElseThrow();
        
        assertThat(evalSpan.getAttributes().get(
            io.opentelemetry.api.common.AttributeKey.stringKey("evaluation.name")))
            .isEqualTo("traced-evaluation");
        
        // Verify task spans
        var taskSpans = spans.stream()
            .filter(s -> s.getName().equals("evaluation.task"))
            .toList();
        assertThat(taskSpans).hasSize(3);
    }
    
    @Test
    void testEvaluationWithExperimentContext() {
        // Given
        var context = BraintrustContext.forExperiment("exp-123")
            .storeInContext(Context.current());
        
        var testData = List.of(1, 2, 3, 4, 5);
        
        // When
        try (var scope = context.makeCurrent()) {
            var results = Evaluation.<Integer, Integer>builder()
                .name("experiment-evaluation")
                .data(testData)
                .task(n -> n * n)
                .scorer("square_check", (input, output) -> 
                    output == input * input ? 1.0 : 0.0)
                .experimentId("exp-123")
                .run();
            
            // Then
            assertThat(results.summary().successCount()).isEqualTo(5);
        }
        
        // Verify experiment parent was set
        var spans = spanExporter.getFinishedSpanItems();
        var evalSpan = spans.stream()
            .filter(s -> s.getName().equals("evaluation"))
            .findFirst()
            .orElseThrow();
        
        assertThat(evalSpan.getAttributes().get(BraintrustSpanProcessor.PARENT_EXPERIMENT_ID))
            .isEqualTo("exp-123");
        assertThat(evalSpan.getAttributes().get(BraintrustSpanProcessor.PARENT_TYPE))
            .isEqualTo("experiment");
    }
    
    @Test
    void testParallelEvaluationWithMetrics() {
        // Given
        var data = IntStream.range(0, 100).boxed().toList();
        var processedCount = new AtomicInteger(0);
        
        // When
        var results = Evaluation.<Integer, String>builder()
            .name("parallel-metrics-eval")
            .data(data)
            .task(n -> {
                // Simulate varying processing time
                try {
                    Thread.sleep(n % 10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                processedCount.incrementAndGet();
                return "Result-" + n;
            })
            .scorer("format_check", (input, output) -> 
                output.equals("Result-" + input) ? 1.0 : 0.0)
            .parallel(true)
            .executor(ForkJoinPool.commonPool())
            .run();
        
        // Then
        assertThat(results.summary().totalCount()).isEqualTo(100);
        assertThat(results.summary().successCount()).isEqualTo(100);
        assertThat(processedCount.get()).isEqualTo(100);
        
        // Verify parallel execution via span timing
        var spans = spanExporter.getFinishedSpanItems();
        var taskSpans = spans.stream()
            .filter(s -> s.getName().equals("evaluation.task"))
            .toList();
        
        assertThat(taskSpans).hasSize(100);
        
        // Check that some spans overlap (parallel execution)
        var overlapFound = false;
        for (int i = 0; i < taskSpans.size() - 1; i++) {
            for (int j = i + 1; j < taskSpans.size(); j++) {
                var span1 = taskSpans.get(i);
                var span2 = taskSpans.get(j);
                
                // Check if spans overlap
                if (span1.getStartEpochNanos() < span2.getEndEpochNanos() &&
                    span2.getStartEpochNanos() < span1.getEndEpochNanos()) {
                    overlapFound = true;
                    break;
                }
            }
            if (overlapFound) break;
        }
        
        assertThat(overlapFound).isTrue();
    }
    
    @Test
    void testEvaluationWithMixedResults() {
        // Given
        record TestCase(int number, boolean shouldFail) {}
        var testCases = List.of(
            new TestCase(1, false),
            new TestCase(2, true),
            new TestCase(3, false),
            new TestCase(4, true),
            new TestCase(5, false)
        );
        
        // When
        var results = Evaluation.<TestCase, Integer>builder()
            .name("mixed-results-eval")
            .data(testCases)
            .task(tc -> {
                if (tc.shouldFail) {
                    throw new RuntimeException("Simulated failure for " + tc.number);
                }
                return tc.number * 10;
            })
            .scorer("validation", (input, output) -> 
                output == input.number * 10 ? 1.0 : 0.0)
            .run();
        
        // Then
        assertThat(results.summary().totalCount()).isEqualTo(5);
        assertThat(results.summary().successCount()).isEqualTo(3);
        assertThat(results.summary().errorCount()).isEqualTo(2);
        
        // Verify error spans have exceptions
        var spans = spanExporter.getFinishedSpanItems();
        var errorSpans = spans.stream()
            .filter(s -> s.getName().equals("evaluation.task"))
            .filter(s -> s.getStatus().getStatusCode() == io.opentelemetry.api.trace.StatusCode.ERROR)
            .toList();
        
        assertThat(errorSpans).hasSize(2);
        errorSpans.forEach(span -> {
            assertThat(span.getEvents()).isNotEmpty();
            assertThat(span.getEvents().get(0).getName()).isEqualTo("exception");
        });
    }
    
    @Test
    void testEvaluationWithMultipleScorers() {
        // Given
        record QA(String question, String expectedAnswer) {}
        var questions = List.of(
            new QA("What is 2+2?", "4"),
            new QA("What is the capital of France?", "Paris"),
            new QA("What color is the sky?", "Blue")
        );
        
        // Simulate an imperfect model
        Map<String, String> model = Map.of(
            "What is 2+2?", "4",
            "What is the capital of France?", "Paris",
            "What color is the sky?", "Blue or grey"  // Not exact match
        );
        
        // When
        var results = Evaluation.<QA, String>builder()
            .name("multi-scorer-eval")
            .data(questions)
            .task(qa -> model.getOrDefault(qa.question, "I don't know"))
            .scorer(Scorer.StringScorers.exactMatch(qa -> qa.expectedAnswer))
            .scorer(Scorer.StringScorers.contains(qa -> qa.expectedAnswer))
            .scorer("length_ratio", (qa, answer) -> {
                if (answer.isEmpty()) return 0.0;
                var ratio = (double) Math.min(answer.length(), qa.expectedAnswer.length()) /
                           Math.max(answer.length(), qa.expectedAnswer.length());
                return ratio;
            })
            .run();
        
        // Then
        assertThat(results.summary().successCount()).isEqualTo(3);
        
        // Check score statistics
        var stats = results.summary().scoreStatistics();
        assertThat(stats).containsKeys("exact_match", "contains", "length_ratio");
        
        // Exact match should have lower average due to "Blue or grey"
        assertThat(stats.get("exact_match").mean()).isLessThan(1.0);
        
        // Contains should be perfect
        assertThat(stats.get("contains").mean()).isEqualTo(1.0);
        
        // Verify scores are recorded in spans
        var spans = spanExporter.getFinishedSpanItems();
        var taskSpans = spans.stream()
            .filter(s -> s.getName().equals("evaluation.task"))
            .toList();
        
        taskSpans.forEach(span -> {
            var attrs = span.getAttributes();
            assertThat(attrs.asMap().keySet().stream()
                .filter(key -> key.getKey().startsWith("score."))
                .count()).isEqualTo(3);
        });
    }
    
    @Test
    void testEvaluationTimeout() {
        // Given
        var data = List.of("fast", "slow", "fast");
        var latch = new CountDownLatch(1);
        
        // When
        var results = Evaluation.<String, String>builder()
            .name("timeout-eval")
            .data(data)
            .task(input -> {
                try {
                    if ("slow".equals(input)) {
                        latch.countDown();
                        Thread.sleep(200); // Will timeout
                    } else {
                        Thread.sleep(10);
                    }
                    return input.toUpperCase();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted", e);
                }
            })
            .timeout(Duration.ofMillis(50))
            .run();
        
        // Then
        assertThat(results.summary().totalCount()).isEqualTo(3);
        assertThat(results.summary().errorCount()).isEqualTo(1);
        
        // Wait for slow task to be interrupted
        try {
            latch.await();
            Thread.sleep(100); // Give time for cleanup
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Verify timeout in failed result
        var failedResult = results.results().stream()
            .filter(r -> !r.isSuccess())
            .findFirst()
            .orElseThrow();
        
        assertThat(failedResult.input()).isEqualTo("slow");
        assertThat(failedResult.error()).isPresent();
        assertThat(failedResult.error().get())
            .isInstanceOf(EvaluationException.class)
            .hasMessageContaining("timed out");
    }
    
    @Test
    void testEvaluationResultsAnalysis() {
        // Given
        var data = IntStream.range(0, 20)
            .mapToObj(i -> Map.entry(i, i % 4)) // Groups of 4
            .toList();
        
        // When
        var results = Evaluation.<Map.Entry<Integer, Integer>, Integer>builder()
            .name("analysis-eval")
            .data(data)
            .task(entry -> entry.getKey() * entry.getValue())
            .scorer("accuracy", (entry, result) -> {
                var expected = entry.getKey() * entry.getValue();
                return result.equals(expected) ? 1.0 : 0.0;
            })
            .scorer("group_bonus", (entry, result) -> {
                // Bonus score for group 0
                return entry.getValue() == 0 ? 1.0 : 0.5;
            })
            .run();
        
        // Then - test analysis methods
        assertThat(results.successful().count()).isEqualTo(20);
        assertThat(results.failed().count()).isEqualTo(0);
        
        var partitioned = results.partitionBySuccess();
        assertThat(partitioned.get(true)).hasSize(20);
        assertThat(partitioned.get(false)).isEmpty();
        
        var sorted = results.sortedByScore();
        assertThat(sorted.get(0).averageScore())
            .isGreaterThanOrEqualTo(sorted.get(sorted.size() - 1).averageScore());
        
        var highScorers = results.withMinimumScore(0.9);
        assertThat(highScorers).hasSize(5); // Group 0 entries
        
        // Test report generation
        var report = results.generateReport();
        assertThat(report).contains("Evaluation: analysis-eval");
        assertThat(report).contains("Total: 20");
        assertThat(report).contains("Success Rate: 100.00%");
        assertThat(report).contains("accuracy:");
        assertThat(report).contains("group_bonus:");
    }
}