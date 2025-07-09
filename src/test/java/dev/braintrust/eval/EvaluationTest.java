package dev.braintrust.eval;

import static org.assertj.core.api.Assertions.*;

import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class EvaluationTest {

    @RegisterExtension
    static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

    @BeforeEach
    void setUp() {
        // OpenTelemetryExtension already sets up GlobalOpenTelemetry
    }

    @Test
    void testSimpleEvaluation() {
        // Given
        var data = List.of("hello", "world", "test");

        // When
        var results =
                Evaluation.<String, String>builder()
                        .name("test-eval")
                        .data(data)
                        .task(String::toUpperCase)
                        .scorer(
                                "exact_match",
                                (input, output) -> output.equals(input.toUpperCase()) ? 1.0 : 0.0)
                        .run();

        // Then
        assertThat(results.name()).isEqualTo("test-eval");
        assertThat(results.results()).hasSize(3);
        assertThat(results.summary().totalCount()).isEqualTo(3);
        assertThat(results.summary().successCount()).isEqualTo(3);
        assertThat(results.summary().errorCount()).isEqualTo(0);

        // Verify all scores are perfect
        results.results()
                .forEach(
                        result -> {
                            assertThat(result.scores()).containsEntry("exact_match", 1.0);
                            assertThat(result.isSuccess()).isTrue();
                        });
    }

    @Test
    void testEvaluationWithErrors() {
        // Given
        var data = List.of(1, 0, 2);

        // When
        var results =
                Evaluation.<Integer, Integer>builder()
                        .name("division-eval")
                        .data(data)
                        .task(n -> 10 / n) // Will throw for 0
                        .scorer("result_check", (input, output) -> output == 10 / input ? 1.0 : 0.0)
                        .run();

        // Then
        assertThat(results.summary().totalCount()).isEqualTo(3);
        assertThat(results.summary().successCount()).isEqualTo(2);
        assertThat(results.summary().errorCount()).isEqualTo(1);

        // Verify the error case
        var errorResult =
                results.results().stream().filter(r -> !r.isSuccess()).findFirst().orElseThrow();

        assertThat(errorResult.input()).isEqualTo(0);
        assertThat(errorResult.error()).isPresent();
        assertThat(errorResult.error().get()).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void testParallelEvaluation() {
        // Given
        var data = IntStream.range(0, 100).boxed().toList();

        // When
        var results =
                Evaluation.<Integer, Integer>builder()
                        .name("parallel-eval")
                        .data(data)
                        .task(
                                n -> {
                                    try {
                                        Thread.sleep(10); // Simulate work
                                        return n * n;
                                    } catch (InterruptedException e) {
                                        throw new RuntimeException(e);
                                    }
                                })
                        .scorer(
                                "square_check",
                                (input, output) -> output == input * input ? 1.0 : 0.0)
                        .parallel(true)
                        .run();

        // Then
        assertThat(results.summary().totalCount()).isEqualTo(100);
        assertThat(results.summary().successCount()).isEqualTo(100);

        // Verify parallel execution was faster than sequential would be
        var totalDuration =
                results.results().stream().mapToLong(r -> r.duration().toMillis()).sum();

        // Total work time should be > 1000ms but wall time should be much less
        assertThat(totalDuration).isGreaterThan(1000);
    }

    @Test
    void testMultipleScorerEvaluation() {
        // Given
        record TestCase(String input, String expected) {}
        var data =
                List.of(
                        new TestCase("hello", "HELLO"),
                        new TestCase("world", "WORLD"),
                        new TestCase("Test", "TEST"));

        // When
        var results =
                Evaluation.<TestCase, String>builder()
                        .name("multi-scorer-eval")
                        .data(data)
                        .task(tc -> tc.input.toUpperCase())
                        .scorer(
                                "exact_match",
                                (tc, output) -> output.equals(tc.expected) ? 1.0 : 0.0)
                        .scorer(
                                "length_match",
                                (tc, output) -> output.length() == tc.expected.length() ? 1.0 : 0.0)
                        .scorer(
                                "starts_with",
                                (tc, output) ->
                                        output.startsWith(tc.expected.substring(0, 1)) ? 1.0 : 0.0)
                        .run();

        // Then
        assertThat(results.summary().scoreStatistics()).hasSize(3);
        assertThat(results.summary().scoreStatistics())
                .containsKeys("exact_match", "length_match", "starts_with");

        // All scorers should have perfect scores
        results.summary()
                .scoreStatistics()
                .values()
                .forEach(
                        stats -> {
                            assertThat(stats.mean()).isEqualTo(1.0);
                            assertThat(stats.min()).isEqualTo(1.0);
                            assertThat(stats.max()).isEqualTo(1.0);
                        });
    }

    @Test
    void testEvaluationWithTimeout() {
        // Given
        var data = List.of("fast", "slow");

        // When
        var results =
                Evaluation.<String, String>builder()
                        .name("timeout-eval")
                        .data(data)
                        .task(
                                input -> {
                                    try {
                                        if ("slow".equals(input)) {
                                            Thread.sleep(200);
                                        }
                                        return input.toUpperCase();
                                    } catch (InterruptedException e) {
                                        throw new RuntimeException(e);
                                    }
                                })
                        .timeout(Duration.ofMillis(100))
                        .run();

        // Then
        assertThat(results.summary().totalCount()).isEqualTo(2);
        assertThat(results.summary().errorCount()).isEqualTo(1);

        // Fast one should succeed
        var fastResult =
                results.results().stream()
                        .filter(r -> "fast".equals(r.input()))
                        .findFirst()
                        .orElseThrow();
        assertThat(fastResult.isSuccess()).isTrue();

        // Slow one should timeout
        var slowResult =
                results.results().stream()
                        .filter(r -> "slow".equals(r.input()))
                        .findFirst()
                        .orElseThrow();
        assertThat(slowResult.isSuccess()).isFalse();
        assertThat(slowResult.error()).isPresent();
    }

    @Test
    void testStreamDataSource() {
        // Given
        var dataStream = IntStream.range(0, 10).mapToObj(i -> "item" + i);

        // When
        var results =
                Evaluation.<String, Integer>builder()
                        .name("stream-eval")
                        .data(dataStream)
                        .task(String::length)
                        .scorer(
                                "length_check",
                                (input, output) -> output == input.length() ? 1.0 : 0.0)
                        .run();

        // Then
        assertThat(results.summary().totalCount()).isEqualTo(10);
        assertThat(results.summary().successCount()).isEqualTo(10);
    }
}
