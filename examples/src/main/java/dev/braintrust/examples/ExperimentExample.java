package dev.braintrust.examples;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import dev.braintrust.config.BraintrustConfig;
import dev.braintrust.eval.Evaluation;
import dev.braintrust.trace.BraintrustTracing;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Scope;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Example demonstrating how to run an experiment/evaluation in Braintrust. This evaluates different
 * prompts for a question-answering task.
 */
public class ExperimentExample {

    // Test case for Q&A evaluation
    record QATestCase(String question, String expectedAnswer, String category) {}

    // Output from our Q&A system
    record QAOutput(String answer, long promptTokens, long completionTokens, double latencyMs) {}

    public static void main(String[] args) {
        // Validate environment
        if (System.getenv("BRAINTRUST_API_KEY") == null) {
            System.err.println("Please set BRAINTRUST_API_KEY environment variable");
            System.exit(1);
        }
        if (System.getenv("OPENAI_API_KEY") == null) {
            System.err.println("Please set OPENAI_API_KEY environment variable");
            System.exit(1);
        }

        // Initialize Braintrust
        var config = BraintrustConfig.fromEnvironment();
        var openTelemetry = BraintrustTracing.quickstart(config);
        var tracer = BraintrustTracing.getTracer(openTelemetry);

        // Initialize OpenAI client
        var openAiClient = OpenAIOkHttpClient.fromEnv();

        // Define test cases
        var testCases =
                List.of(
                        new QATestCase("What is the capital of France?", "Paris", "geography"),
                        new QATestCase("What is 2 + 2?", "4", "math"),
                        new QATestCase("Who wrote Romeo and Juliet?", "Shakespeare", "literature"),
                        new QATestCase(
                                "What is the largest planet in our solar system?",
                                "Jupiter",
                                "science"),
                        new QATestCase("What year did World War II end?", "1945", "history"));

        // Run experiment with simple prompt
        System.out.println("=== Running Experiment: Simple Prompt ===");
        runExperiment(
                "QA Simple Prompt v1",
                testCases,
                "Answer the following question concisely:",
                openAiClient,
                tracer);

        // Run experiment with detailed prompt
        System.out.println("\n=== Running Experiment: Detailed Prompt ===");
        runExperiment(
                "QA Detailed Prompt v1",
                testCases,
                "You are a knowledgeable assistant. Answer the following question accurately and"
                        + " concisely. If you're not sure, say 'I don't know':",
                openAiClient,
                tracer);

        // Run experiment with chain-of-thought prompt
        System.out.println("\n=== Running Experiment: Chain-of-Thought Prompt ===");
        runExperiment(
                "QA Chain-of-Thought v1",
                testCases,
                "Think step by step and answer the following question. Show your reasoning briefly,"
                        + " then give the final answer:",
                openAiClient,
                tracer);

        System.out.println("\n=== Experiments Complete ===");
        System.out.println("View results at: https://www.braintrust.dev");

        // Wait for traces to export
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void runExperiment(
            String experimentName,
            List<QATestCase> testCases,
            String systemPrompt,
            OpenAIClient openAiClient,
            io.opentelemetry.api.trace.Tracer tracer) {
        // Build the evaluation
        var evaluation =
                Evaluation.<QATestCase, QAOutput>builder()
                        .name(experimentName)
                        .data(testCases)
                        .task(
                                testCase -> {
                                    try {
                                        return evaluateTestCase(
                                                        testCase,
                                                        systemPrompt,
                                                        openAiClient,
                                                        tracer)
                                                .get();
                                    } catch (Exception e) {
                                        throw new RuntimeException(
                                                "Failed to evaluate test case", e);
                                    }
                                })
                        .scorer(
                                "correctness",
                                (testCase, output) -> {
                                    // Check if the answer contains the expected answer
                                    // (case-insensitive)
                                    var answerLower = output.answer.toLowerCase();
                                    var expectedLower = testCase.expectedAnswer.toLowerCase();
                                    return answerLower.contains(expectedLower) ? 1.0 : 0.0;
                                })
                        .scorer(
                                "response_time",
                                (testCase, output) -> {
                                    // Score based on response time (faster is better)
                                    // Perfect score for < 100ms, 0 score for > 2000ms
                                    if (output.latencyMs < 100) return 1.0;
                                    if (output.latencyMs > 2000) return 0.0;
                                    return 1.0 - (output.latencyMs - 100) / 1900.0;
                                })
                        .parallel(true) // Run test cases in parallel
                        .build();

        // Run the evaluation
        System.out.println("Running evaluation: " + experimentName);
        var results = evaluation.run();

        // Print summary
        var summary = results.summary();
        System.out.printf("Completed: %d test cases%n", summary.totalCount());
        System.out.printf("Success rate: %.1f%%%n", summary.successRate() * 100);

        // Print score statistics
        summary.scoreStatistics()
                .forEach(
                        (scorer, stats) -> {
                            System.out.printf(
                                    "%s - Mean: %.2f, Min: %.2f, Max: %.2f%n",
                                    scorer, stats.mean(), stats.min(), stats.max());
                        });

        System.out.printf("Average duration: %.0f ms%n", summary.averageDuration().toMillis());
    }

    private static CompletableFuture<QAOutput> evaluateTestCase(
            QATestCase testCase,
            String systemPrompt,
            OpenAIClient client,
            io.opentelemetry.api.trace.Tracer tracer) {
        return CompletableFuture.supplyAsync(
                () -> {
                    var span =
                            tracer.spanBuilder("evaluate_qa")
                                    .setSpanKind(SpanKind.INTERNAL)
                                    .setAttribute("test.question", testCase.question)
                                    .setAttribute("test.category", testCase.category)
                                    .startSpan();

                    try (Scope scope = span.makeCurrent()) {
                        var startTime = System.currentTimeMillis();

                        // Create OpenAI request
                        var request =
                                ChatCompletionCreateParams.builder()
                                        .model(ChatModel.GPT_4O)
                                        .addSystemMessage(systemPrompt)
                                        .addUserMessage(testCase.question)
                                        .maxTokens(100L)
                                        .temperature(0.1) // Low temperature for consistent answers
                                        .build();

                        // Call OpenAI
                        var response = client.chat().completions().create(request);
                        var answer = response.choices().get(0).message().content().orElse("");

                        var latency = System.currentTimeMillis() - startTime;

                        // Extract token usage
                        var usage = response.usage().orElse(null);
                        var promptTokens = usage != null ? usage.promptTokens() : 0;
                        var completionTokens = usage != null ? usage.completionTokens() : 0;

                        // Add span attributes
                        span.setAttribute("llm.model", "gpt-4o");
                        span.setAttribute("llm.prompt_tokens", promptTokens);
                        span.setAttribute("llm.completion_tokens", completionTokens);
                        span.setAttribute("llm.latency_ms", latency);
                        span.setAttribute("llm.response", answer);

                        span.setStatus(io.opentelemetry.api.trace.StatusCode.OK);

                        return new QAOutput(answer, promptTokens, completionTokens, latency);

                    } catch (Exception e) {
                        span.recordException(e);
                        span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, e.getMessage());
                        throw new RuntimeException("Failed to evaluate test case", e);
                    } finally {
                        span.end();
                    }
                });
    }
}
