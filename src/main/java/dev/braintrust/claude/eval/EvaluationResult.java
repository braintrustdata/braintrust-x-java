package dev.braintrust.claude.eval;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Represents the result of evaluating a single test case.
 *
 * @param input The input data for this test case
 * @param output The output produced by the task (null if error occurred)
 * @param scores Map of scorer names to their scores
 * @param duration How long the task took to execute
 * @param error Any error that occurred during execution
 */
public record EvaluationResult<INPUT, OUTPUT>(
        INPUT input,
        @Nullable OUTPUT output,
        Map<String, Double> scores,
        Duration duration,
        Optional<Exception> error) {

    /** Returns whether this evaluation was successful (no errors). */
    public boolean isSuccess() {
        return error.isEmpty();
    }

    /** Returns the average score across all scorers. */
    public double averageScore() {
        if (scores.isEmpty()) {
            return 0.0;
        }
        return scores.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    /** Gets a specific score by name. */
    public Optional<Double> getScore(String scorerName) {
        return Optional.ofNullable(scores.get(scorerName));
    }
}
