package dev.braintrust.claude.eval;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Container for all evaluation results with summary statistics. */
public record EvaluationResults<INPUT, OUTPUT>(
        String name, List<EvaluationResult<INPUT, OUTPUT>> results, EvaluationSummary summary) {

    /** Returns only successful results. */
    public Stream<EvaluationResult<INPUT, OUTPUT>> successful() {
        return results.stream().filter(EvaluationResult::isSuccess);
    }

    /** Returns only failed results. */
    public Stream<EvaluationResult<INPUT, OUTPUT>> failed() {
        return results.stream().filter(r -> !r.isSuccess());
    }

    /** Groups results by their success status. */
    public Map<Boolean, List<EvaluationResult<INPUT, OUTPUT>>> partitionBySuccess() {
        return results.stream().collect(Collectors.partitioningBy(EvaluationResult::isSuccess));
    }

    /** Returns results sorted by average score (descending). */
    public List<EvaluationResult<INPUT, OUTPUT>> sortedByScore() {
        return results.stream()
                .sorted((a, b) -> Double.compare(b.averageScore(), a.averageScore()))
                .toList();
    }

    /** Filters results by minimum average score. */
    public List<EvaluationResult<INPUT, OUTPUT>> withMinimumScore(double threshold) {
        return results.stream().filter(r -> r.averageScore() >= threshold).toList();
    }

    /** Creates a detailed report of the evaluation. */
    public String generateReport() {
        var sb = new StringBuilder();
        sb.append("Evaluation: ").append(name).append("\n");
        sb.append("=====================================\n\n");

        sb.append("Summary:\n");
        sb.append("  Total: ").append(summary.totalCount()).append("\n");
        sb.append("  Successful: ").append(summary.successCount()).append("\n");
        sb.append("  Failed: ").append(summary.errorCount()).append("\n");
        sb.append("  Success Rate: ")
                .append(String.format("%.2f%%", summary.successRate() * 100))
                .append("\n");
        sb.append("  Avg Duration: ").append(summary.averageDuration().toMillis()).append("ms\n\n");

        if (!summary.scoreStatistics().isEmpty()) {
            sb.append("Score Statistics:\n");
            summary.scoreStatistics()
                    .forEach(
                            (scorer, stats) -> {
                                sb.append("  ").append(scorer).append(":\n");
                                sb.append("    Mean: ")
                                        .append(String.format("%.4f", stats.mean()))
                                        .append("\n");
                                sb.append("    Min: ")
                                        .append(String.format("%.4f", stats.min()))
                                        .append("\n");
                                sb.append("    Max: ")
                                        .append(String.format("%.4f", stats.max()))
                                        .append("\n");
                                sb.append("    Median: ")
                                        .append(String.format("%.4f", stats.median()))
                                        .append("\n");
                                sb.append("    Std Dev: ")
                                        .append(String.format("%.4f", stats.standardDeviation()))
                                        .append("\n");
                            });
        }

        return sb.toString();
    }
}
