package dev.braintrust.examples;

import dev.braintrust.config.BraintrustConfig;
import dev.braintrust.eval.Evaluation;
import dev.braintrust.eval.Scorer;
import dev.braintrust.trace.BraintrustTracing;
import java.time.Duration;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * Example demonstrating the evaluation framework with idiomatic Java. Shows use of records,
 * streams, lambdas, and method references.
 */
public class EvaluationExample {

    // Test case using Java record
    record MathProblem(String question, int expected) {}

    // AI model simulator
    static class SimpleMathModel {
        private final Random random = new Random();

        public String solve(String question) {
            // Extract numbers from question (simplified)
            var numbers =
                    Pattern.compile("\\d+")
                            .matcher(question)
                            .results()
                            .map(mr -> Integer.parseInt(mr.group()))
                            .toList();

            if (numbers.size() >= 2) {
                // Simulate solving with some error
                var result = numbers.get(0) + numbers.get(1);
                if (random.nextDouble() > 0.9) {
                    result += random.nextInt(3) - 1; // Add small error
                }
                return String.valueOf(result);
            }
            return "Unable to solve";
        }
    }

    public static void main(String[] args) {
        // Initialize Braintrust
        var config = BraintrustConfig.fromEnvironment();
        BraintrustTracing.quickstart(config);

        // Create test dataset using streams
        var testData =
                IntStream.range(1, 21)
                        .mapToObj(
                                i ->
                                        new MathProblem(
                                                String.format("What is %d + %d?", i, i * 2),
                                                i + i * 2))
                        .toList();

        var model = new SimpleMathModel();

        // Run evaluation with multiple scorers
        var results =
                Evaluation.<MathProblem, String>builder()
                        .name("Math Problem Evaluation")
                        .data(testData)
                        .task(problem -> model.solve(problem.question))
                        .scorer(Scorer.StringScorers.exactMatch(p -> String.valueOf(p.expected)))
                        .scorer(createNumericScorer())
                        .scorer(createPartialCreditScorer())
                        .parallel(true)
                        .timeout(Duration.ofSeconds(5))
                        .executor(ForkJoinPool.commonPool())
                        .run();

        // Print comprehensive report
        System.out.println(results.generateReport());

        // Analyze results using streams
        System.out.println("\nDetailed Analysis:");

        // Group by score ranges
        var scoreDistribution =
                results.results().stream()
                        .collect(
                                java.util.stream.Collectors.groupingBy(
                                        r -> (int) (r.averageScore() * 10) / 10.0,
                                        java.util.stream.Collectors.counting()));

        System.out.println("Score Distribution:");
        scoreDistribution.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(
                        entry ->
                                System.out.printf(
                                        "  %.1f: %d results%n", entry.getKey(), entry.getValue()));

        // Find problematic cases
        System.out.println("\nProblematic Cases (score < 0.5):");
        results.results().stream()
                .filter(r -> r.averageScore() < 0.5)
                .forEach(
                        r ->
                                System.out.printf(
                                        "  Q: %s, Expected: %d, Got: %s%n",
                                        r.input().question, r.input().expected, r.output()));

        // Performance metrics
        var durations =
                results.results().stream()
                        .mapToLong(r -> r.duration().toMillis())
                        .summaryStatistics();

        System.out.printf("\nPerformance Metrics:%n");
        System.out.printf("  Min: %dms%n", durations.getMin());
        System.out.printf("  Max: %dms%n", durations.getMax());
        System.out.printf("  Avg: %.2fms%n", durations.getAverage());

        // Wait for spans to export
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static Scorer<MathProblem, String> createNumericScorer() {
        return Scorer.of(
                "numeric_accuracy",
                (problem, output) -> {
                    try {
                        var actual = Integer.parseInt(output);
                        var expected = problem.expected;
                        var error = Math.abs(actual - expected);

                        // Give partial credit for close answers
                        return switch (error) {
                            case 0 -> 1.0;
                            case 1 -> 0.8;
                            case 2 -> 0.5;
                            default -> 0.0;
                        };
                    } catch (NumberFormatException e) {
                        return 0.0;
                    }
                });
    }

    private static Scorer<MathProblem, String> createPartialCreditScorer() {
        return new Scorer<MathProblem, String>() {
            @Override
            public double score(MathProblem input, String output) {
                // Check if output contains the correct answer
                var expectedStr = String.valueOf(input.expected);
                if (output.contains(expectedStr)) {
                    return output.equals(expectedStr) ? 1.0 : 0.7;
                }

                // Check if it's a valid number
                try {
                    Integer.parseInt(output);
                    return 0.3; // Valid number but wrong
                } catch (NumberFormatException e) {
                    return 0.0; // Not a number
                }
            }

            @Override
            public String name() {
                return "partial_credit";
            }
        };
    }
}
