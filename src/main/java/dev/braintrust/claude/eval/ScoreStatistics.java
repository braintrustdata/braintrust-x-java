package dev.braintrust.claude.eval;

/** Statistical summary of scores from an evaluation. */
public record ScoreStatistics(
        double mean, double min, double max, double median, double standardDeviation, long count) {

    /** Returns the range (max - min). */
    public double range() {
        return max - min;
    }

    /** Returns the coefficient of variation (CV). */
    public double coefficientOfVariation() {
        return mean == 0 ? 0.0 : standardDeviation / mean;
    }

    /** Formats the statistics as a human-readable string. */
    @Override
    public String toString() {
        return String.format(
                "ScoreStats{mean=%.4f, min=%.4f, max=%.4f, median=%.4f, stdDev=%.4f, n=%d}",
                mean, min, max, median, standardDeviation, count);
    }
}
