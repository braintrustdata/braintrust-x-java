package dev.braintrust.eval;

import dev.braintrust.trace.BraintrustSpanProcessor;
import dev.braintrust.trace.BraintrustTracing;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;

/**
 * Main evaluation framework for testing AI models. Uses Java generics for type safety and streams
 * for functional processing.
 *
 * @param <INPUT> The type of input data for the evaluation
 * @param <OUTPUT> The type of output produced by the task
 */
public final class Evaluation<INPUT, OUTPUT> {
    private final String name;
    private final Iterable<INPUT> data;
    private final Function<INPUT, OUTPUT> task;
    private final List<Scorer<INPUT, OUTPUT>> scorers;
    private final EvaluationOptions options;
    private final Tracer tracer;

    private Evaluation(Builder<INPUT, OUTPUT> builder) {
        this.name = builder.name;
        this.data = builder.data;
        this.task = builder.task;
        this.scorers = List.copyOf(builder.scorers);
        this.options = builder.options;
        this.tracer = BraintrustTracing.getTracer();
    }

    /** Runs the evaluation and returns results. */
    public EvaluationResults<INPUT, OUTPUT> run() {
        var span =
                tracer.spanBuilder("evaluation")
                        .setSpanKind(SpanKind.INTERNAL)
                        .setAttribute("evaluation.name", name)
                        .startSpan();

        try (var scope = span.makeCurrent()) {
            return options.experimentId != null ? runWithExperiment(span) : runStandalone(span);
        } finally {
            span.end();
        }
    }

    /** Runs the evaluation asynchronously. */
    public CompletableFuture<EvaluationResults<INPUT, OUTPUT>> runAsync() {
        return CompletableFuture.supplyAsync(this::run, options.executor);
    }

    private EvaluationResults<INPUT, OUTPUT> runStandalone(Span parentSpan) {
        var results =
                streamData()
                        .map(input -> evaluateOne(input, parentSpan))
                        .collect(Collectors.toList());

        return new EvaluationResults<>(name, results, calculateSummary(results));
    }

    private EvaluationResults<INPUT, OUTPUT> runWithExperiment(Span parentSpan) {
        parentSpan.setAttribute(BraintrustSpanProcessor.PARENT_EXPERIMENT_ID, options.experimentId);
        parentSpan.setAttribute(BraintrustSpanProcessor.PARENT_TYPE, "experiment");

        return runStandalone(parentSpan);
    }

    private Stream<INPUT> streamData() {
        return data instanceof Collection<INPUT> collection
                ? options.parallel ? collection.parallelStream() : collection.stream()
                : StreamSupport.stream(data.spliterator(), options.parallel);
    }

    private EvaluationResult<INPUT, OUTPUT> evaluateOne(INPUT input, Span parentSpan) {
        var span =
                tracer.spanBuilder("evaluation.task")
                        .setParent(Context.current().with(parentSpan))
                        .setSpanKind(SpanKind.INTERNAL)
                        .startSpan();

        try (var scope = span.makeCurrent()) {
            var startTime = Instant.now();

            // Run the task
            OUTPUT output;
            @Nullable Exception error = null;

            try {
                output = options.timeout != null ? runWithTimeout(input) : task.apply(input);
            } catch (Exception e) {
                output = null;
                error = e;
                span.recordException(e);
            }

            var duration = Duration.between(startTime, Instant.now());

            // Calculate scores
            var scores = calculateScores(input, output, error);

            // Add scores to span
            scores.forEach(
                    (name, score) -> {
                        span.setAttribute("score." + name, score);
                    });

            return new EvaluationResult<>(
                    input, output, scores, duration, Optional.ofNullable(error));
        } finally {
            span.end();
        }
    }

    private OUTPUT runWithTimeout(INPUT input) throws Exception {
        var future = CompletableFuture.supplyAsync(() -> task.apply(input), options.executor);
        try {
            return future.get(options.timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new EvaluationException("Task timed out after " + options.timeout, e);
        } catch (ExecutionException e) {
            throw new EvaluationException("Task execution failed", e.getCause());
        }
    }

    private Map<String, Double> calculateScores(
            INPUT input, @Nullable OUTPUT output, @Nullable Exception error) {
        if (error != null || scorers.isEmpty()) {
            return Map.of();
        }

        return scorers.stream()
                .collect(
                        Collectors.toMap(
                                Scorer::name,
                                scorer -> scorer.score(input, output),
                                (a, b) -> b, // In case of duplicate names, use the last one
                                LinkedHashMap::new // Preserve order
                                ));
    }

    private EvaluationSummary calculateSummary(List<EvaluationResult<INPUT, OUTPUT>> results) {
        var totalCount = results.size();
        var errorCount = (int) results.stream().filter(r -> r.error().isPresent()).count();
        var successCount = totalCount - errorCount;

        var avgDuration =
                results.stream().mapToLong(r -> r.duration().toMillis()).average().orElse(0.0);

        var scoreStats = calculateScoreStatistics(results);

        return new EvaluationSummary(
                totalCount,
                successCount,
                errorCount,
                Duration.ofMillis((long) avgDuration),
                scoreStats);
    }

    private Map<String, ScoreStatistics> calculateScoreStatistics(
            List<EvaluationResult<INPUT, OUTPUT>> results) {
        var scoresByName = new HashMap<String, List<Double>>();

        results.stream()
                .flatMap(r -> r.scores().entrySet().stream())
                .forEach(
                        entry ->
                                scoresByName
                                        .computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                                        .add(entry.getValue()));

        return scoresByName.entrySet().stream()
                .collect(
                        Collectors.toMap(
                                Map.Entry::getKey, entry -> calculateStats(entry.getValue())));
    }

    private ScoreStatistics calculateStats(List<Double> values) {
        var stats = values.stream().mapToDouble(Double::doubleValue).summaryStatistics();

        var sorted = values.stream().sorted().toList();
        var median =
                sorted.size() % 2 == 0
                        ? (sorted.get(sorted.size() / 2 - 1) + sorted.get(sorted.size() / 2)) / 2.0
                        : sorted.get(sorted.size() / 2);

        return new ScoreStatistics(
                stats.getAverage(),
                stats.getMin(),
                stats.getMax(),
                median,
                calculateStdDev(values, stats.getAverage()),
                stats.getCount());
    }

    private double calculateStdDev(List<Double> values, double mean) {
        var variance =
                values.stream().mapToDouble(v -> Math.pow(v - mean, 2)).average().orElse(0.0);
        return Math.sqrt(variance);
    }

    /** Creates a new evaluation builder. */
    public static <I, O> Builder<I, O> builder() {
        return new Builder<>();
    }

    /** Builder for creating evaluations with fluent API. */
    public static final class Builder<INPUT, OUTPUT> {
        private String name = "evaluation";
        private Iterable<INPUT> data = List.of();
        private Function<INPUT, OUTPUT> task;
        private final List<Scorer<INPUT, OUTPUT>> scorers = new ArrayList<>();
        private final EvaluationOptions options = new EvaluationOptions();

        public Builder<INPUT, OUTPUT> name(String name) {
            this.name = name;
            return this;
        }

        public Builder<INPUT, OUTPUT> data(Iterable<INPUT> data) {
            this.data = data;
            return this;
        }

        public Builder<INPUT, OUTPUT> data(Stream<INPUT> data) {
            this.data = data.toList();
            return this;
        }

        @SafeVarargs
        public final Builder<INPUT, OUTPUT> data(INPUT... data) {
            this.data = List.of(data);
            return this;
        }

        public Builder<INPUT, OUTPUT> task(Function<INPUT, OUTPUT> task) {
            this.task = task;
            return this;
        }

        public Builder<INPUT, OUTPUT> scorer(Scorer<INPUT, OUTPUT> scorer) {
            this.scorers.add(scorer);
            return this;
        }

        public Builder<INPUT, OUTPUT> scorer(
                String name, BiFunction<INPUT, OUTPUT, Double> scoreFunc) {
            return scorer(Scorer.of(name, scoreFunc));
        }

        public Builder<INPUT, OUTPUT> experimentId(String experimentId) {
            this.options.experimentId = experimentId;
            return this;
        }

        public Builder<INPUT, OUTPUT> parallel(boolean parallel) {
            this.options.parallel = parallel;
            return this;
        }

        public Builder<INPUT, OUTPUT> timeout(Duration timeout) {
            this.options.timeout = timeout;
            return this;
        }

        public Builder<INPUT, OUTPUT> executor(ExecutorService executor) {
            this.options.executor = executor;
            return this;
        }

        public Evaluation<INPUT, OUTPUT> build() {
            if (task == null) {
                throw new IllegalStateException("Task function is required");
            }
            return new Evaluation<>(this);
        }

        public EvaluationResults<INPUT, OUTPUT> run() {
            return build().run();
        }

        public CompletableFuture<EvaluationResults<INPUT, OUTPUT>> runAsync() {
            return build().runAsync();
        }
    }

    private static final class EvaluationOptions {
        @Nullable String experimentId;
        boolean parallel = false;
        @Nullable Duration timeout;
        ExecutorService executor = ForkJoinPool.commonPool();
    }
}
