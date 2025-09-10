package dev.braintrust.claude.examples;

import dev.braintrust.claude.api.BraintrustApiClient;
import dev.braintrust.claude.config.BraintrustConfig;
import dev.braintrust.claude.eval.Evaluation;
import dev.braintrust.claude.trace.BraintrustTracing;
import java.util.List;
import java.util.Optional;

/**
 * Example showing how to create an experiment via the API and use it with evaluations. This
 * demonstrates the full workflow of experiment management in Braintrust.
 */
public class ExperimentWithApiExample {

    record TestCase(String input, String expected) {}

    record Output(String result, double confidence) {}

    public static void main(String[] args) throws Exception {
        // Initialize Braintrust
        var config = BraintrustConfig.fromEnvironment();
        var apiClient = new BraintrustApiClient(config);
        var openTelemetry = BraintrustTracing.quickstart(config);

        // Step 1: Create or get a project
        System.out.println("=== Setting up Project ===");
        var projectName = "Experiment API Example";
        var projects = apiClient.listProjects().get();

        var project =
                projects.stream()
                        .filter(p -> p.name().equals(projectName))
                        .findFirst()
                        .orElseGet(
                                () -> {
                                    try {
                                        System.out.println("Creating new project: " + projectName);
                                        return apiClient.createProject(projectName).get();
                                    } catch (Exception e) {
                                        throw new RuntimeException("Failed to create project", e);
                                    }
                                });

        System.out.println("Using project: " + project.name() + " (ID: " + project.id() + ")");

        // Step 2: Create an experiment
        System.out.println("\n=== Creating Experiment ===");
        var experimentName = "Text Classification v" + System.currentTimeMillis();
        var experiment =
                apiClient
                        .createExperiment(
                                new BraintrustApiClient.CreateExperimentRequest(
                                        project.id(),
                                        experimentName,
                                        Optional.of("Testing text classification accuracy"),
                                        Optional.empty() // No base experiment
                                        ))
                        .get();

        System.out.println(
                "Created experiment: " + experiment.name() + " (ID: " + experiment.id() + ")");

        // Step 3: Define test data
        var testCases =
                List.of(
                        new TestCase("I love this product!", "positive"),
                        new TestCase("This is terrible", "negative"),
                        new TestCase("It's okay, nothing special", "neutral"),
                        new TestCase("Amazing experience, highly recommend!", "positive"),
                        new TestCase("Waste of money", "negative"),
                        new TestCase("Average quality for the price", "neutral"));

        // Step 4: Run evaluation with the experiment
        System.out.println("\n=== Running Evaluation ===");
        var evaluation =
                Evaluation.<TestCase, Output>builder()
                        .name("Sentiment Analysis")
                        .data(testCases)
                        .task(testCase -> classifyText(testCase.input))
                        .scorer(
                                "accuracy",
                                (test, output) -> output.result.equals(test.expected) ? 1.0 : 0.0)
                        .scorer("confidence", (test, output) -> output.confidence)
                        .experimentId(experiment.id()) // Link to the experiment
                        .build();

        var results = evaluation.run();

        // Step 5: Print results
        System.out.println("\n=== Results ===");
        var summary = results.summary();
        System.out.printf("Total test cases: %d%n", summary.totalCount());
        System.out.printf("Success rate: %.1f%%%n", summary.successRate() * 100);

        summary.scoreStatistics()
                .forEach(
                        (scorer, stats) -> {
                            System.out.printf("\n%s:%n", scorer);
                            System.out.printf("  Mean: %.3f%n", stats.mean());
                            System.out.printf("  Min: %.3f%n", stats.min());
                            System.out.printf("  Max: %.3f%n", stats.max());
                        });

        // Step 6: Compare with previous experiments (if any)
        System.out.println("\n=== Experiment Comparison ===");
        var allExperiments = apiClient.listExperiments(project.id()).get();
        System.out.printf("Total experiments in project: %d%n", allExperiments.size());

        // In a real scenario, you would compare metrics across experiments
        System.out.println("\nView detailed results at: https://www.braintrust.dev");
        System.out.println("Project: " + project.name());
        System.out.println("Experiment: " + experiment.name());

        // Wait for traces to export
        Thread.sleep(3000);
    }

    // Simulated text classifier
    private static Output classifyText(String text) {
        var lowerText = text.toLowerCase();

        // Simple keyword-based classification
        if (lowerText.contains("love")
                || lowerText.contains("amazing")
                || lowerText.contains("great")
                || lowerText.contains("recommend")) {
            return new Output("positive", 0.9);
        } else if (lowerText.contains("terrible")
                || lowerText.contains("hate")
                || lowerText.contains("waste")
                || lowerText.contains("bad")) {
            return new Output("negative", 0.85);
        } else if (lowerText.contains("okay")
                || lowerText.contains("average")
                || lowerText.contains("nothing special")) {
            return new Output("neutral", 0.7);
        } else {
            // Default to neutral with lower confidence
            return new Output("neutral", 0.5);
        }
    }
}
