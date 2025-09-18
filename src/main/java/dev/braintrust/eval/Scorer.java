package dev.braintrust.eval;

import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A scorer evaluates the result of a test case with a score between 0 (inclusive) and 1 (inclusive).
 *
 * @param <INPUT> type of the input data
 * @param <OUTPUT> type of the output data
 */
public interface Scorer<INPUT, OUTPUT> {
    String getName();

    double score(EvalCase<INPUT, OUTPUT> evalCase, OUTPUT result);

    static <INPUT, OUTPUT> Scorer<INPUT, OUTPUT> of(String scorerName, BiFunction<EvalCase<INPUT, OUTPUT>, OUTPUT, Double> scorerFn) {
        return new Scorer<>() {
            @Override
            public String getName() {
                return scorerName;
            }

            @Override
            public double score(EvalCase<INPUT, OUTPUT> evalCase, OUTPUT result) {
                return scorerFn.apply(evalCase, result);
            }
        };
    }

    static <INPUT, OUTPUT, RESULT> Scorer<INPUT, OUTPUT> of(String scorerName, Function<OUTPUT, Double> scorerFn) {
        return of(scorerName, (evalCase, result) -> scorerFn.apply(result));
    }
}
