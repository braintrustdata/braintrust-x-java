package dev.braintrust.eval;

import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A scorer evaluates the result of a test case with a score between 0 (inclusive) and 1 (inclusive).
 */
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
