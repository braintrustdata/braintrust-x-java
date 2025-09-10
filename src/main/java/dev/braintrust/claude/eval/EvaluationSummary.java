package dev.braintrust.claude.eval;

import java.time.Duration;
import java.util.Map;

/** Summary statistics for an evaluation run. */
public record EvaluationSummary(
        int totalCount,
        int successCount,
        int errorCount,
        Duration averageDuration,
        Map<String, ScoreStatistics> scoreStatistics) {

    /** Calculates the success rate as a percentage. */
    public double successRate() {
        return totalCount == 0 ? 0.0 : (double) successCount / totalCount;
    }

    /** Calculates the error rate as a percentage. */
    public double errorRate() {
        return totalCount == 0 ? 0.0 : (double) errorCount / totalCount;
    }
}
