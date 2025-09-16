package dev.braintrust.eval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.braintrust.api.BraintrustApiClient;
import dev.braintrust.claude.trace.BraintrustSpanProcessor;
import dev.braintrust.config.BraintrustConfig;
import dev.braintrust.trace.BraintrustTracing;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Main evaluation framework for testing AI models. Uses Java generics for type safety and streams
 * for functional processing.
 *
 * @param <INPUT> The type of input data for the evaluation
 * @param <EXPECTED> The type of output produced by the task
 */
public final class Eval<INPUT, EXPECTED, RESULT> {
    private static final ObjectMapper JSON_MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();
    private final @Nonnull String experimentName;
    private final @Nonnull String projectName;
    private final @Nonnull BraintrustConfig config;
    private final @Nonnull BraintrustApiClient client;
    private final @Nonnull Tracer tracer;
    private final @Nonnull List<EvalCase<INPUT, EXPECTED>> evalCases;
    private final @Nonnull Task<INPUT, EXPECTED, RESULT> task;
    private final @Nonnull List<Scorer<INPUT, EXPECTED, RESULT>> scorers;

    private Eval(Builder<INPUT, EXPECTED, RESULT> builder) {
        this.experimentName = builder.experimentName;
        this.projectName = Objects.requireNonNull(builder.projectName);
        this.config = Objects.requireNonNull(builder.config);
        this.client = new BraintrustApiClient(config);
        this.tracer = Objects.requireNonNull(builder.tracer);
        this.evalCases = List.copyOf(builder.evalCases);
        this.task = Objects.requireNonNull(builder.task);
        this.scorers = List.copyOf(builder.scorers);
    }

    /** Runs the evaluation and returns results. */
    public EvalResult<INPUT, EXPECTED, RESULT> run() {
        var experiment = client.createExperiment(new BraintrustApiClient.CreateExperimentRequest(projectName, experimentName, Optional.empty(), Optional.empty()));
        var experimentID = experiment.id();
        var evalCaseResults = evalCases.stream()
                .map(evalCase -> evalOne(experimentID, evalCase))
                .toList();
        return new EvalResult<>();
    }

    private EvalCaseResult<INPUT, EXPECTED, RESULT> evalOne(String experimentId, EvalCase<INPUT, EXPECTED> evalCase) {
        var rootSpan =
                tracer.spanBuilder("eval") // TODO: allow names for eval cases
                        .setNoParent() // each eval case is its own trace
                        .setSpanKind(SpanKind.CLIENT)
                        .setAttribute(BraintrustSpanProcessor.PARENT, "experiment_id:" + experimentId)
                        .setAttribute("braintrust.span_attributes", "{\"type\":\"eval\"}")
                        // FIXME: use proper object mapper for json stuff
                        .setAttribute("braintrust.input_json", "{ \"input\":\"" + evalCase.input() + "\"}")
                        .setAttribute("braintrust.expected", "\"" + evalCase.expected() + "\"")
                        // TODO: these attributes are deprecated apparently? Do we need to set them?
                        .setAttribute(BraintrustSpanProcessor.PARENT_EXPERIMENT_ID, experimentId)
                        .setAttribute(BraintrustSpanProcessor.PARENT_TYPE, "experiment")
                        .startSpan();
        try (var rootScope = rootSpan.makeCurrent()) {
            final RESULT result;
            { // run task
                var taskSpan = tracer.spanBuilder("task")
                        .setAttribute(BraintrustSpanProcessor.PARENT, "experiment_id:" + experimentId)
                        .setAttribute("braintrust.span_attributes", "{\"type\":\"task\"}")
                        .startSpan();
                try (var unused = taskSpan.makeCurrent()){
                    result = task.apply(evalCase);
                } finally {
                    taskSpan.end();
                }
                rootSpan.setAttribute("braintrust.output_json", "{ \"output\":\"" + result + "\"}");
            }
            { // run scorers
                var scoreSpan = tracer.spanBuilder("score")
                        .setAttribute(BraintrustSpanProcessor.PARENT, "experiment_id:" + experimentId)
                        .setAttribute("braintrust.span_attributes", "{\"type\":\"score\"}")
                        .startSpan();
                try (var unused = scoreSpan.makeCurrent()){
                    // NOTE: linked hash map to preserve ordering. Not in the spec but nice user experience
                    final HashMap<String, Double> nameToScore = new LinkedHashMap<>();
                    var scores = scorers.stream()
                            .map(scorer -> {
                                var score = scorer.score(evalCase, result);
                                nameToScore.put(scorer.getName(), score);
                                return score;
                            })
                            .toList();
                    try {
                        scoreSpan.setAttribute("braintrust.scores", JSON_MAPPER.writeValueAsString(nameToScore));
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                } finally {
                    scoreSpan.end();
                }
            }
            return new EvalCaseResult<>(evalCase, result);
        } finally {
            rootSpan.end();
        }
    }

    /**
     * Results of all eval cases of an experiment.
     */
    public static class EvalResult<INPUT, EXPECTED, RESULT> {
        private EvalResult() {}

        public String createReportString() {
            // some status about the eval scorers
            // a link to the experiment on the UI
            throw new RuntimeException("~~~~~ TODO: put a link here to view the experiment");
        }
    }

    public record EvalCaseResult<INPUT, EXPECTED, RESULT>(EvalCase<INPUT, EXPECTED> evalCase, RESULT result) {}

    public record EvalCase<INPUT, EXPECTED>(INPUT input, EXPECTED expected, @Nonnull List<String> tags, @Nonnull Metadata metadata) {
        public static <INPUT, EXPECTED> EvalCase<INPUT, EXPECTED> of(INPUT input, EXPECTED expected) {
            return of(input, expected, List.of(), new Metadata());
        }
        public static <INPUT, EXPECTED> EvalCase<INPUT, EXPECTED> of(INPUT input, EXPECTED expected, @Nonnull List<String> tags, @Nonnull Metadata metadata) {
            return new EvalCase<>(input, expected, tags, metadata);
        }
    }

    public record Metadata() {
        // TODO implement: arbitrary map of string->json
    }

    public interface Task<INPUT, EXPECTED, RESULT> {
        RESULT apply(EvalCase<INPUT, EXPECTED> evalCase);
    }

    public interface Scorer<INPUT, EXPECTED, RESULT> {
        String getName();

        double score(EvalCase<INPUT, EXPECTED> evalCase, RESULT result);

        static <INPUT, EXPECTED, RESULT> Scorer<INPUT, EXPECTED, RESULT> of(String scorerName, BiFunction<EvalCase<INPUT, EXPECTED>, RESULT, Double> scorerFn) {
            return new Scorer<>() {
                @Override
                public String getName() {
                    return scorerName;
                }

                @Override
                public double score(EvalCase<INPUT, EXPECTED> evalCase, RESULT result) {
                    return scorerFn.apply(evalCase, result);
                }
            };
        }

        static <INPUT, EXPECTED, RESULT> Scorer<INPUT, EXPECTED, RESULT> of(String scorerName, Function<RESULT, Double> scorerFn) {
            return of(scorerName, (evalCase, result) -> scorerFn.apply(result));
        }
    }

    /** Creates a new eval builder. */
    public static <INPUT, EXPECTED, RESULT> Builder<INPUT, EXPECTED, RESULT> builder() {
        return new Builder<>();
    }

    /** Builder for creating evaluations with fluent API. */
    public static final class Builder<INPUT, EXPECTED, RESULT> {
        private @Nonnull String experimentName = "unnamed-java-eval";
        private @Nullable BraintrustConfig config;
        private @Nullable String projectName;
        private @Nullable Tracer tracer = null;
        private @Nonnull List<EvalCase<INPUT, EXPECTED>> evalCases = List.of();
        private @Nullable Task<INPUT, EXPECTED, RESULT> task;
        private @Nonnull List<Scorer<INPUT, EXPECTED, RESULT>> scorers = List.of();

        public Eval<INPUT, EXPECTED, RESULT> build() {
            if (config == null) {
                config = BraintrustConfig.fromEnvironment();
            }
            if (tracer == null) {
                tracer = BraintrustTracing.getTracer();
            }
            if (projectName == null) {
                projectName = config.defaultProjectId().orElse(BraintrustConfig.FALLBACK_PROJECT_NAME);
            }
            if (evalCases.isEmpty()) {
                throw new RuntimeException("must provide at least one eval case");
            }
            if (scorers.isEmpty()) {
                throw new RuntimeException("must provide at least one scorer");
            }
            Objects.requireNonNull(task);
            return new Eval<>(this);
        }

        public Builder<INPUT, EXPECTED, RESULT> name(String name) {
            this.experimentName = name;
            return this;
        }

        public Builder<INPUT, EXPECTED, RESULT> projectName(@Nonnull String projectName) {
            this.projectName = Objects.requireNonNull(projectName);
            return this;
        }

        public Builder<INPUT, EXPECTED, RESULT> config(BraintrustConfig config) {
            this.config = config;
            return this;
        }

        public Builder<INPUT, EXPECTED, RESULT> tracer(Tracer tracer) {
            this.tracer = tracer;
            return this;
        }

        @SafeVarargs
        public final Builder<INPUT, EXPECTED, RESULT> cases(EvalCase<INPUT, EXPECTED>... cases) {
            this.evalCases = List.of(cases);
            return this;
        }

        public Builder<INPUT, EXPECTED, RESULT> task(Task<INPUT, EXPECTED, RESULT> task) {
            this.task = task;
            return this;
        }

        @SafeVarargs
        public final Builder<INPUT, EXPECTED, RESULT> scorers(Scorer<INPUT, EXPECTED, RESULT>... scorers) {
            this.scorers = List.of(scorers);
            return this;
        }
    }
}
