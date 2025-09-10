package dev.braintrust.claude.examples;

import dev.braintrust.claude.api.Experiment;
import dev.braintrust.claude.api.Project;
import dev.braintrust.claude.config.BraintrustConfig;
import dev.braintrust.claude.eval.Evaluation;
import dev.braintrust.claude.trace.BraintrustContext;
import dev.braintrust.claude.trace.BraintrustTracing;
import io.opentelemetry.context.Context;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Example showing the same DX as the Go SDK with automatic experiment registration. This mirrors
 * the Go examples where RegisterExperiment is called directly.
 */
public class SimpleExperimentWithRegistration {

    record MathProblem(String question, double expected) {}

    record Answer(double result, long computeTimeMs) {}

    public static void main(String[] args) throws Exception {
        // Initialize Braintrust
        var config = BraintrustConfig.fromEnvironment();
        var openTelemetry = BraintrustTracing.quickstart(config);

        System.out.println("=== Braintrust Experiment Example (Go SDK Style) ===\n");

        // Step 1: Register a project (same as Go: api.RegisterProject)
        System.out.println("Registering project...");
        var project = Project.registerProjectSync("Math Solver Evaluation");
        System.out.println("Project ID: " + project.id());

        // Step 2: Register an experiment (same as Go: api.RegisterExperiment)
        var experimentName = "accuracy-test-" + System.currentTimeMillis();
        System.out.println("\nRegistering experiment...");
        var experiment = Experiment.registerExperimentSync(experimentName, project.id());
        System.out.println("Experiment ID: " + experiment.id());

        // Get the app URL from config
        var appUrl = config.appUrl().toString().replaceAll("/$", "");
        // URLEncoder.encode uses + for spaces, but we need %20 for URL paths
        var projectNameEncoded =
                java.net.URLEncoder.encode(project.name(), java.nio.charset.StandardCharsets.UTF_8)
                        .replace("+", "%20");

        // For staging, we need to handle the org differently
        var baseExperimentUrl =
                appUrl.contains("staging")
                        ? String.format(
                                "%s/app/%s/p/%s/experiments/%s",
                                appUrl, project.orgId(), projectNameEncoded, experimentName)
                        : String.format(
                                "%s/app/braintrustdata.com/p/%s/experiments/%s",
                                appUrl, projectNameEncoded, experimentName);

        // TODO: Add comparison parameter if there's a previous experiment
        var experimentUrl = baseExperimentUrl;

        System.out.println("\nExperiment " + experimentName + " is running at " + experimentUrl);

        // Step 3: Set up tracing context with experiment (similar to Go's trace.SetParent)
        var braintrustContext = BraintrustContext.forExperiment(experiment.id());
        var context = braintrustContext.storeInContext(Context.current()).makeCurrent();

        // Step 4: Define test data
        var problems =
                List.of(
                        new MathProblem("What is 2 + 2?", 4.0),
                        new MathProblem("What is 10 * 5?", 50.0),
                        new MathProblem("What is 100 / 4?", 25.0),
                        new MathProblem("What is 7 * 8?", 56.0),
                        new MathProblem("What is 15 - 9?", 6.0));

        // Step 5: Run evaluation with the experiment
        System.out.println("\nRunning evaluation...");
        System.out.printf("Math Problem Solver (data): %d items%n", problems.size());

        var evaluation =
                Evaluation.<MathProblem, Answer>builder()
                        .name("Math Problem Solver")
                        .data(problems)
                        .task(SimpleExperimentWithRegistration::solveMathProblem)
                        .scorer(
                                "accuracy",
                                (problem, answer) ->
                                        Math.abs(answer.result - problem.expected) < 0.0001
                                                ? 1.0
                                                : 0.0)
                        .scorer(
                                "speed",
                                (problem, answer) ->
                                        answer.computeTimeMs < 10
                                                ? 1.0
                                                : answer.computeTimeMs < 50 ? 0.5 : 0.0)
                        .experimentId(experiment.id()) // Link to the registered experiment
                        .build();

        var startTime = System.currentTimeMillis();
        var results = evaluation.run();
        var endTime = System.currentTimeMillis();

        System.out.printf(
                "Math Problem Solver (tasks): %d/%d completed in %.2fs%n",
                problems.size(), problems.size(), (endTime - startTime) / 1000.0);

        // Step 6: Print results
        System.out.println("\n========================= SUMMARY =========================");
        System.out.println(experimentName + ":");

        var summary = results.summary();

        // Score summaries
        summary.scoreStatistics()
                .forEach(
                        (scorer, stats) -> {
                            System.out.printf(
                                    "%.2f%% '%s' score (mean: %.3f, min: %.3f, max: %.3f)%n",
                                    stats.mean() * 100,
                                    scorer,
                                    stats.mean(),
                                    stats.min(),
                                    stats.max());
                        });

        System.out.println();
        System.out.printf(
                "%.2fs duration (%.2fs total across %d items)%n",
                (endTime - startTime) / 1000.0,
                summary.averageDuration().toMillis() * summary.totalCount() / 1000.0,
                summary.totalCount());

        if (summary.errorCount() > 0) {
            System.out.printf(
                    "%d errors (%.1f%% error rate)%n",
                    summary.errorCount(), (summary.errorCount() * 100.0) / summary.totalCount());
        }

        System.out.println("\n=== View in Braintrust ===");
        System.out.println("See results for " + experimentName + " at " + experimentUrl);

        // Clean up context
        context.close();

        // Force flush all spans and shutdown properly
        System.out.println("\nFlushing traces to Braintrust...");
        if (openTelemetry instanceof io.opentelemetry.sdk.OpenTelemetrySdk sdk) {
            // First force flush to ensure all spans are sent
            var flushResult = sdk.getSdkTracerProvider().forceFlush();
            if (!flushResult.join(10, TimeUnit.SECONDS).isSuccess()) {
                System.err.println("Warning: Some spans may not have been flushed");
            }

            // Give a bit more time for any in-flight exports to complete
            Thread.sleep(1000);

            // Now shutdown gracefully
            var shutdownResult = sdk.getSdkTracerProvider().shutdown();
            if (shutdownResult.join(10, TimeUnit.SECONDS).isSuccess()) {
                System.out.println("Traces flushed and exporter shut down successfully!");
            } else {
                System.err.println("Warning: Shutdown may not have completed cleanly");
            }
        } else {
            // Fallback: just wait a bit for traces to be exported
            Thread.sleep(5000);
            System.out.println("Waited for trace export.");
        }
    }

    // Even simpler: Use the convenience method that does both project and experiment
    public static void demonstrateSimplestApproach() throws Exception {
        System.out.println("\n=== Simplest Approach (like Go's GetOrCreateExperiment) ===");

        var problems =
                List.of(
                        new MathProblem("What is 5 + 5?", 10.0),
                        new MathProblem("What is 3 * 3?", 9.0));

        // This single line replaces project + experiment registration!
        var evaluation =
                Evaluation.<MathProblem, Answer>builder()
                        .name("Quick Math Test")
                        .data(problems)
                        .task(SimpleExperimentWithRegistration::solveMathProblem)
                        .scorer(
                                "accuracy",
                                (problem, answer) ->
                                        Math.abs(answer.result - problem.expected) < 0.0001
                                                ? 1.0
                                                : 0.0)
                        // This automatically creates project + experiment (like Go's helper)
                        .experiment("quick-math-experiment", "Quick Math Project")
                        .build();

        var results = evaluation.run();
        System.out.println("Evaluation complete! Check Braintrust dashboard.");
    }

    private static Answer solveMathProblem(MathProblem problem) {
        var start = System.currentTimeMillis();

        // Parse and solve the math problem (simplified)
        var result =
                switch (problem.question) {
                    case "What is 2 + 2?" -> 4.0;
                    case "What is 10 * 5?" -> 50.0;
                    case "What is 100 / 4?" -> 25.0;
                    case "What is 7 * 8?" -> 56.0;
                    case "What is 15 - 9?" -> 6.0;
                    case "What is 5 + 5?" -> 10.0;
                    case "What is 3 * 3?" -> 9.0;
                    default -> 0.0; // Wrong answer for unknown problems
                };

        // Simulate some computation time
        try {
            Thread.sleep((long) (Math.random() * 20));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        var computeTime = System.currentTimeMillis() - start;
        return new Answer(result, computeTime);
    }
}
