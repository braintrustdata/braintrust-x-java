package dev.braintrust.examples;

import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import dev.braintrust.api.Experiment;
import dev.braintrust.api.Project;
import dev.braintrust.config.BraintrustConfig;
import dev.braintrust.eval.Evaluation;
import dev.braintrust.openai.BraintrustOpenAI;
import dev.braintrust.trace.BraintrustContext;
import dev.braintrust.trace.BraintrustTracing;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Example showing a real evaluation with OpenAI API calls and scoring. This evaluates an LLM's
 * ability to answer trivia questions correctly.
 */
public class TriviaEvaluation {

    record TriviaQuestion(String question, String expectedAnswer) {}

    record LLMResponse(String answer, long latencyMs, int promptTokens, int completionTokens) {}

    private static OpenTelemetry openTelemetry;
    private static Tracer tracer;
    private static BraintrustOpenAI wrappedOpenAI;

    public static void main(String[] args) throws Exception {
        // Initialize Braintrust
        var config = BraintrustConfig.fromEnvironment();
        openTelemetry = BraintrustTracing.quickstart(config);
        tracer = openTelemetry.getTracer("trivia-evaluation");

        System.out.println("=== Braintrust Trivia Evaluation ===\n");

        // Initialize OpenAI client
        var openAIKey = System.getenv("OPENAI_API_KEY");
        if (openAIKey == null || openAIKey.isEmpty()) {
            System.err.println("Error: OPENAI_API_KEY environment variable not set!");
            System.exit(1);
        }

        var openai = OpenAIOkHttpClient.builder().apiKey(openAIKey).build();
        wrappedOpenAI = BraintrustOpenAI.wrap(openai, tracer);

        // Step 1: Register project
        System.out.println("Registering project...");
        var project = Project.registerProjectSync("Trivia Bot Evaluation");
        System.out.println("Project ID: " + project.id());

        // Step 2: Register experiment
        var experimentName = "trivia-eval-" + System.currentTimeMillis();
        System.out.println("\nRegistering experiment...");
        var experiment = Experiment.registerExperimentSync(experimentName, project.id());
        System.out.println("Experiment ID: " + experiment.id());

        // Show experiment URL
        var appUrl = config.appUrl().toString().replaceAll("/$", "");
        var projectNameEncoded =
                java.net.URLEncoder.encode(project.name(), java.nio.charset.StandardCharsets.UTF_8)
                        .replace("+", "%20");
        var experimentUrl =
                String.format(
                        "%s/app/%s/p/%s/experiments/%s",
                        appUrl, project.orgId(), projectNameEncoded, experimentName);

        System.out.println("\nExperiment " + experimentName + " is running at " + experimentUrl);

        // Step 3: Set up tracing context
        var braintrustContext = BraintrustContext.forExperiment(experiment.id());
        var context = braintrustContext.storeInContext(Context.current()).makeCurrent();

        // Step 4: Define trivia questions dataset
        var questions =
                List.of(
                        new TriviaQuestion("Which country has the largest population?", "China"),
                        new TriviaQuestion("What is the capital of France?", "Paris"),
                        new TriviaQuestion("Who painted the Mona Lisa?", "Leonardo da Vinci"),
                        new TriviaQuestion(
                                "What is the largest planet in our solar system?", "Jupiter"),
                        new TriviaQuestion("In what year did World War II end?", "1945"));

        // Step 5: Run evaluation
        System.out.println("\nRunning evaluation...");
        System.out.printf("Trivia Bot (data): %d questions%n", questions.size());

        var evaluation =
                Evaluation.<TriviaQuestion, LLMResponse>builder()
                        .name("Trivia Bot")
                        .data(questions)
                        .task(TriviaEvaluation::askLLM)
                        .scorer(
                                "accuracy",
                                (question, response) -> {
                                    if (response == null || response.answer == null) {
                                        return 0.0;
                                    }
                                    // Simple string comparison (case-insensitive)
                                    var normalizedExpected =
                                            question.expectedAnswer.toLowerCase().trim();
                                    var normalizedActual = response.answer.toLowerCase().trim();
                                    return normalizedActual.contains(normalizedExpected)
                                            ? 1.0
                                            : 0.0;
                                })
                        .scorer(
                                "response_time",
                                (question, response) -> {
                                    if (response == null) return 0.0;
                                    // Score based on response time (faster is better)
                                    if (response.latencyMs < 1000) return 1.0;
                                    if (response.latencyMs < 2000) return 0.5;
                                    return 0.0;
                                })
                        .experimentId(experiment.id())
                        .build();

        var startTime = System.currentTimeMillis();
        var results = evaluation.run();
        var endTime = System.currentTimeMillis();

        System.out.printf(
                "Trivia Bot (tasks): %d/%d completed in %.2fs%n",
                questions.size(), questions.size(), (endTime - startTime) / 1000.0);

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

        // Calculate total tokens
        var totalPromptTokens =
                results.results().stream()
                        .mapToInt(r -> r.output() != null ? r.output().promptTokens : 0)
                        .sum();
        var totalCompletionTokens =
                results.results().stream()
                        .mapToInt(r -> r.output() != null ? r.output().completionTokens : 0)
                        .sum();

        System.out.printf(
                "%.2fs duration (%.2fs average per question)%n",
                (endTime - startTime) / 1000.0, summary.averageDuration().toMillis() / 1000.0);

        System.out.printf(
                "%d prompt tokens, %d completion tokens (%d total)%n",
                totalPromptTokens,
                totalCompletionTokens,
                totalPromptTokens + totalCompletionTokens);

        if (summary.errorCount() > 0) {
            System.out.printf(
                    "%d errors (%.1f%% error rate)%n",
                    summary.errorCount(), (summary.errorCount() * 100.0) / summary.totalCount());
        }

        System.out.println("\n=== View in Braintrust ===");
        System.out.println("See results for " + experimentName + " at " + experimentUrl);

        // Clean up
        context.close();

        // Flush traces
        System.out.println("\nFlushing traces to Braintrust...");
        if (openTelemetry instanceof io.opentelemetry.sdk.OpenTelemetrySdk sdk) {
            var flushResult = sdk.getSdkTracerProvider().forceFlush();
            if (!flushResult.join(10, TimeUnit.SECONDS).isSuccess()) {
                System.err.println("Warning: Some spans may not have been flushed");
            }
            Thread.sleep(1000);
            var shutdownResult = sdk.getSdkTracerProvider().shutdown();
            if (shutdownResult.join(10, TimeUnit.SECONDS).isSuccess()) {
                System.out.println("Traces flushed and exporter shut down successfully!");
            }
        }
    }

    private static LLMResponse askLLM(TriviaQuestion question) {
        var startTime = System.currentTimeMillis();

        try {
            var systemMessage =
                    "You are a helpful assistant that answers trivia questions. Give"
                            + " brief, direct answers without explanation.";

            var request =
                    ChatCompletionCreateParams.builder()
                            .model(ChatModel.GPT_4O_MINI) // Using GPT-4o-mini for cost efficiency
                            .addSystemMessage(systemMessage)
                            .addUserMessage(question.question())
                            .maxTokens(50L)
                            .temperature(0.0) // Deterministic for consistent results
                            .build();

            // Create message info for tracing
            var messages =
                    List.of(
                            BraintrustOpenAI.MessageInfo.system(systemMessage),
                            BraintrustOpenAI.MessageInfo.user(question.question()));

            // Use the wrapped client which will handle all tracing
            var response = wrappedOpenAI.chat().completions().create(request, messages);
            var endTime = System.currentTimeMillis();

            var answer = response.choices().get(0).message().content().orElse("");
            var usage = response.usage().orElse(null);

            return new LLMResponse(
                    answer,
                    endTime - startTime,
                    usage != null ? (int) usage.promptTokens() : 0,
                    usage != null ? (int) usage.completionTokens() : 0);

        } catch (Exception e) {
            System.err.println("Error calling OpenAI: " + e.getMessage());
            return new LLMResponse(null, System.currentTimeMillis() - startTime, 0, 0);
        }
    }
}
