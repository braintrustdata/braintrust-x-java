package dev.braintrust.eval;

import dev.braintrust.claude.api.Experiment;
import dev.braintrust.claude.eval.*;
import dev.braintrust.claude.trace.BraintrustTracing;
import dev.braintrust.config.BraintrustConfig;
import io.opentelemetry.api.trace.Tracer;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Main evaluation framework for testing AI models. Uses Java generics for type safety and streams
 * for functional processing.
 *
 * @param <INPUT> The type of input data for the evaluation
 * @param <EXPECTED> The type of output produced by the task
 */
public final class Eval<INPUT, EXPECTED, RESULT> {
    private final String name;
    private final Tracer tracer;
    private final List<Case> cases;

    private Eval(Builder<INPUT, EXPECTED, RESULT> builder) {
        this.name = builder.name;
        this.tracer = builder.tracer;
        this.cases = List.of();
        throw new RuntimeException("FIXME");
    }

    /** Runs the evaluation and returns results. */
    public EvalResult run() {
        throw new RuntimeException("TODO");
    }

    /**
     * Results of all eval cases of an experiment.
     */
    public static class EvalResult<INPUT, EXPECTED, RESULT> {
        // List: Case->res
        private final List<CaseResult<INPUT, EXPECTED, RESULT>> results = null;

        public String createReportString() {
            // some status about the eval scorers
            // a link to the experiment on the UI
            throw new RuntimeException("TODO");
        }
    }

    public record CaseResult<INPUT, EXPECTED, RESULT>(Case<INPUT, EXPECTED> evalCase, RESULT result) {}

    public record Case<INPUT, EXPECTED>(INPUT input, EXPECTED expected) {
        public static <INPUT, EXPECTED> Case<INPUT, EXPECTED> of(INPUT input, EXPECTED expected) {
            throw new RuntimeException("TODO");
        }
        public static <INPUT, EXPECTED> Case<INPUT, EXPECTED> of(INPUT input, EXPECTED expected, List<String> tags, Metadata Metadata) {
            throw new RuntimeException("TODO");
        }
    }

    public record Metadata() {
        // TODO implement: arbitrary map of string->json
    }

    public interface Task<INPUT, EXPECTED, RESULT> {
        RESULT run(Case<INPUT, EXPECTED> evalCase);
    }

    public interface Scorer<INPUT, EXPECTED, RESULT> {
        String getName();

        double score(Case<INPUT, EXPECTED> evalCase, RESULT result);

        static <INPUT, EXPECTED, RESULT> Scorer<INPUT, EXPECTED, RESULT> of(String scorerName, BiFunction<Case<INPUT, EXPECTED>, RESULT, Double> scorerFn) {
            return new Scorer<>() {
                @Override
                public String getName() {
                    return scorerName;
                }

                @Override
                public double score(Case<INPUT, EXPECTED> evalCase, RESULT result) {
                    return scorerFn.apply(evalCase, result);
                }
            };
        }

        static <INPUT, EXPECTED, RESULT> Scorer<INPUT, EXPECTED, RESULT> of(String scorerName, Function<RESULT, Double> scorerFn) {
            throw new RuntimeException("TODO");
        }
    }

    /** Creates a new eval builder. */
    public static <INPUT, EXPECTED, RESULT> Builder<INPUT, EXPECTED, RESULT> builder() {
        return new Builder<>();
    }

    /** Builder for creating evaluations with fluent API. */
    public static final class Builder<INPUT, EXPECTED, RESULT> {
        private String name = "unnamed-java-eval";
        private @Nullable Tracer tracer = null;
        private List<Case<INPUT, EXPECTED>> evalCases;
        private Task<INPUT, EXPECTED, RESULT> task;
        private List<Scorer<INPUT, EXPECTED, RESULT>> scorers;

        public Builder<INPUT, EXPECTED, RESULT> name(String name) {
            this.name = name;
            return this;
        }

        public Builder<INPUT, EXPECTED, RESULT> config(BraintrustConfig config) {
            throw new RuntimeException("TODO");
        }

        public Builder<INPUT, EXPECTED, RESULT> tracer(Tracer tracer) {
            this.tracer = tracer;
        }

        @SafeVarargs
        public final Builder<INPUT, EXPECTED, RESULT> cases(Case<INPUT, EXPECTED>... cases) {
            throw new RuntimeException("TODO");
        }

        public Builder<INPUT, EXPECTED, RESULT> task(Task<INPUT, EXPECTED, RESULT> task) {
            throw new RuntimeException("TODO");
        }

        public Builder<INPUT, EXPECTED, RESULT> scorers(List<Scorer<INPUT, EXPECTED, RESULT>> scorers) {
            throw new RuntimeException("TODO");
        }

        public Builder<INPUT, EXPECTED, RESULT> scorer(Scorer<INPUT, EXPECTED, RESULT> scorer) {
            return scorers(List.of(scorer));
        }

        public Eval<INPUT, EXPECTED, RESULT> build() {
            throw new RuntimeException("TODO");
        }
    }
}
