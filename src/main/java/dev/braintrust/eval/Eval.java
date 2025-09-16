package dev.braintrust.eval;

import dev.braintrust.api.BraintrustApiClient;
import dev.braintrust.claude.api.Experiment;
import dev.braintrust.config.BraintrustConfig;
import dev.braintrust.trace.BraintrustTracing;
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
    private final @Nonnull String experimentName;
    private final @Nonnull String projectName;
    private final @Nonnull BraintrustConfig config;
    private final @Nonnull BraintrustApiClient client;
    private final @Nonnull Tracer tracer;
    private final @Nonnull List<EvalCase> cases;

    private Eval(Builder<INPUT, EXPECTED, RESULT> builder) {
        this.experimentName = builder.experimentName;
        this.projectName = Objects.requireNonNull(builder.projectName);
        this.config = Objects.requireNonNull(builder.config);
        this.client = new BraintrustApiClient(config);
        this.tracer = Objects.requireNonNull(builder.tracer);
        this.cases = List.of();
    }

    /** Runs the evaluation and returns results. */
    public EvalResult<INPUT, EXPECTED, RESULT> run() {
        var experiment = client.createExperiment(new BraintrustApiClient.CreateExperimentRequest(projectName, experimentName, Optional.of("TODO EXP DESC GOES HERE?"), Optional.empty()));
        var experimentID = experiment.id();
        throw new RuntimeException("TODO: " + experimentID);
    }

    private EvalCaseResult<INPUT, EXPECTED, RESULT> evalOne(String experimentId, EvalCase<INPUT, EXPECTED> evalCase) {
        throw new RuntimeException("TODO");
    }

    /**
     * Results of all eval cases of an experiment.
     */
    public static class EvalResult<INPUT, EXPECTED, RESULT> {
        // List: Case->res
        private final List<EvalCaseResult<INPUT, EXPECTED, RESULT>> results = null;

        public String createReportString() {
            // some status about the eval scorers
            // a link to the experiment on the UI
            throw new RuntimeException("TODO");
        }
    }

    public record EvalCaseResult<INPUT, EXPECTED, RESULT>(EvalCase<INPUT, EXPECTED> evalCase, RESULT result) {}

    public record EvalCase<INPUT, EXPECTED>(INPUT input, EXPECTED expected, @Nonnull List<String> tags, @Nonnull Metadata metadata) {
        public static <INPUT, EXPECTED> EvalCase<INPUT, EXPECTED> of(INPUT input, EXPECTED expected) {
            return of(input, expected, List.of(), new Metadata());
        }
        public static <INPUT, EXPECTED> EvalCase<INPUT, EXPECTED> of(INPUT input, EXPECTED expected, List<String> tags, Metadata metadata) {
            return new EvalCase<>(input, expected, tags, metadata);
        }
    }

    public record Metadata() {
        // TODO implement: arbitrary map of string->json
    }

    public interface Task<INPUT, EXPECTED, RESULT> {
        RESULT run(EvalCase<INPUT, EXPECTED> evalCase);
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

        public Builder<INPUT, EXPECTED, RESULT> scorers(List<Scorer<INPUT, EXPECTED, RESULT>> scorers) {
            this.scorers = scorers;
            return this;
        }

        public Builder<INPUT, EXPECTED, RESULT> scorer(Scorer<INPUT, EXPECTED, RESULT> scorer) {
            return scorers(List.of(scorer));
        }
    }
}
