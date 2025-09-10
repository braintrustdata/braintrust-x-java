package dev.braintrust.claude.examples;

import dev.braintrust.claude.config.BraintrustConfig;
import dev.braintrust.claude.eval.Evaluation;
import dev.braintrust.claude.trace.BraintrustTracing;
import java.util.List;
import java.util.Random;

/**
 * Simple experiment example that doesn't require external APIs. Demonstrates evaluating a basic
 * string matching algorithm.
 */
public class SimpleExperimentExample {

    // Test case for string matching
    record MatchTestCase(String text, String pattern, boolean shouldMatch) {}

    // Output from our matcher
    record MatchOutput(boolean matched, long durationNanos, String algorithm) {}

    public static void main(String[] args) {
        // Initialize Braintrust
        var config = BraintrustConfig.fromEnvironment();
        var openTelemetry = BraintrustTracing.quickstart(config);

        // Define test cases
        var testCases =
                List.of(
                        new MatchTestCase("hello world", "world", true),
                        new MatchTestCase("hello world", "World", false), // Case sensitive
                        new MatchTestCase("the quick brown fox", "quick", true),
                        new MatchTestCase("the quick brown fox", "slow", false),
                        new MatchTestCase("OpenTelemetry is great", "Telemetry", true),
                        new MatchTestCase("Braintrust evaluation", "eval", true),
                        new MatchTestCase("", "test", false),
                        new MatchTestCase("test", "", true), // Empty pattern matches
                        new MatchTestCase("abcdefghijklmnop", "mnop", true),
                        new MatchTestCase("performance test", "perf", true));

        // Run experiment with simple contains
        System.out.println("=== Experiment 1: Simple Contains Matcher ===");
        runExperiment(
                "String Matcher - Contains",
                testCases,
                SimpleExperimentExample::simpleContainsMatcher);

        // Run experiment with regex matcher
        System.out.println("\n=== Experiment 2: Regex Matcher ===");
        runExperiment("String Matcher - Regex", testCases, SimpleExperimentExample::regexMatcher);

        // Run experiment with fuzzy matcher (simulated)
        System.out.println("\n=== Experiment 3: Fuzzy Matcher ===");
        runExperiment("String Matcher - Fuzzy", testCases, SimpleExperimentExample::fuzzyMatcher);

        System.out.println("\n=== All Experiments Complete ===");
        System.out.println("Check your Braintrust dashboard to compare results!");

        // Wait for export
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void runExperiment(String name, List<MatchTestCase> testCases, Matcher matcher) {
        var evaluation =
                Evaluation.<MatchTestCase, MatchOutput>builder()
                        .name(name)
                        .data(testCases)
                        .task(testCase -> matcher.match(testCase))
                        .scorer(
                                "accuracy",
                                (testCase, output) -> {
                                    var correct = output.matched == testCase.shouldMatch;
                                    return correct ? 1.0 : 0.0;
                                })
                        .build();

        System.out.println("Running: " + name);
        var results = evaluation.run();
        var summary = results.summary();

        // Calculate average score from the "accuracy" scorer
        var accuracyStats = summary.scoreStatistics().get("accuracy");
        var avgScore = accuracyStats != null ? accuracyStats.mean() : 0.0;

        System.out.printf(
                "Results: %d/%d correct (%.1f%%)%n",
                (int) (avgScore * testCases.size()), testCases.size(), avgScore * 100);
    }

    // Different matcher implementations

    interface Matcher {
        MatchOutput match(MatchTestCase testCase);
    }

    private static MatchOutput simpleContainsMatcher(MatchTestCase testCase) {
        var start = System.nanoTime();
        var matched = testCase.text.contains(testCase.pattern);
        var duration = System.nanoTime() - start;

        // Simulate some processing time
        simulateWork(10);

        return new MatchOutput(matched, duration, "contains");
    }

    private static MatchOutput regexMatcher(MatchTestCase testCase) {
        var start = System.nanoTime();
        var matched = testCase.text.matches(".*" + testCase.pattern + ".*");
        var duration = System.nanoTime() - start;

        // Simulate some processing time
        simulateWork(20);

        return new MatchOutput(matched, duration, "regex");
    }

    private static MatchOutput fuzzyMatcher(MatchTestCase testCase) {
        var start = System.nanoTime();

        // Simple fuzzy matching: allow case-insensitive and partial matches
        var matched = testCase.text.toLowerCase().contains(testCase.pattern.toLowerCase());

        // Add some randomness to simulate fuzzy matching confidence
        if (matched && testCase.pattern.length() > 3) {
            // Sometimes "miss" long patterns to simulate fuzzy behavior
            matched = new Random().nextDouble() > 0.1;
        }

        var duration = System.nanoTime() - start;

        // Simulate more processing time for fuzzy matching
        simulateWork(50);

        return new MatchOutput(matched, duration, "fuzzy");
    }

    private static void simulateWork(int microseconds) {
        try {
            Thread.sleep(0, microseconds * 1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
