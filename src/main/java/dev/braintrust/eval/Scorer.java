package dev.braintrust.eval;

import java.util.function.BiFunction;

/**
 * Represents a scoring function for evaluation results.
 * Can be implemented as a functional interface or using the factory methods.
 * 
 * @param <INPUT> The type of input data
 * @param <OUTPUT> The type of output data
 */
@FunctionalInterface
public interface Scorer<INPUT, OUTPUT> {
    
    /**
     * Calculates a score for the given input and output.
     * 
     * @param input The input to the task
     * @param output The output produced by the task
     * @return A score between 0.0 and 1.0, where 1.0 is perfect
     */
    double score(INPUT input, OUTPUT output);
    
    /**
     * Returns the name of this scorer.
     * Default implementation returns the class name.
     */
    default String name() {
        return getClass().getSimpleName();
    }
    
    /**
     * Creates a scorer with a specific name.
     */
    static <I, O> Scorer<I, O> of(String name, BiFunction<I, O, Double> scoreFunc) {
        return new Scorer<I, O>() {
            @Override
            public double score(I input, O output) {
                return scoreFunc.apply(input, output);
            }
            
            @Override
            public String name() {
                return name;
            }
        };
    }
    
    /**
     * Common scorers for string outputs.
     */
    final class StringScorers {
        private StringScorers() {}
        
        /**
         * Exact match scorer.
         */
        public static <I> Scorer<I, String> exactMatch(java.util.function.Function<I, String> expectedFunc) {
            return of("exact_match", (input, output) -> {
                var expected = expectedFunc.apply(input);
                return expected.equals(output) ? 1.0 : 0.0;
            });
        }
        
        /**
         * Levenshtein distance scorer (normalized).
         */
        public static <I> Scorer<I, String> levenshtein(java.util.function.Function<I, String> expectedFunc) {
            return of("levenshtein", (input, output) -> {
                var expected = expectedFunc.apply(input);
                if (expected == null || output == null) {
                    return 0.0;
                }
                
                var distance = calculateLevenshteinDistance(expected, output);
                var maxLength = Math.max(expected.length(), output.length());
                
                return maxLength == 0 ? 1.0 : 1.0 - (double) distance / maxLength;
            });
        }
        
        /**
         * Contains substring scorer.
         */
        public static <I> Scorer<I, String> contains(java.util.function.Function<I, String> substringFunc) {
            return of("contains", (input, output) -> {
                var substring = substringFunc.apply(input);
                return output != null && output.contains(substring) ? 1.0 : 0.0;
            });
        }
        
        /**
         * Regex match scorer.
         */
        public static <I> Scorer<I, String> regex(java.util.function.Function<I, java.util.regex.Pattern> patternFunc) {
            return of("regex_match", (input, output) -> {
                if (output == null) return 0.0;
                var pattern = patternFunc.apply(input);
                return pattern.matcher(output).matches() ? 1.0 : 0.0;
            });
        }
        
        private static int calculateLevenshteinDistance(String s1, String s2) {
            int[][] dp = new int[s1.length() + 1][s2.length() + 1];
            
            for (int i = 0; i <= s1.length(); i++) {
                dp[i][0] = i;
            }
            
            for (int j = 0; j <= s2.length(); j++) {
                dp[0][j] = j;
            }
            
            for (int i = 1; i <= s1.length(); i++) {
                for (int j = 1; j <= s2.length(); j++) {
                    if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                        dp[i][j] = dp[i - 1][j - 1];
                    } else {
                        dp[i][j] = 1 + Math.min(dp[i - 1][j - 1], 
                                       Math.min(dp[i - 1][j], dp[i][j - 1]));
                    }
                }
            }
            
            return dp[s1.length()][s2.length()];
        }
    }
    
    /**
     * Common scorers for numeric outputs.
     */
    final class NumericScorers {
        private NumericScorers() {}
        
        /**
         * Absolute error scorer (normalized by expected value).
         */
        public static <I> Scorer<I, Number> absoluteError(java.util.function.Function<I, Number> expectedFunc, double threshold) {
            return of("absolute_error", (input, output) -> {
                if (output == null) return 0.0;
                
                var expected = expectedFunc.apply(input).doubleValue();
                var actual = output.doubleValue();
                var error = Math.abs(expected - actual);
                
                return error <= threshold ? 1.0 : Math.max(0.0, 1.0 - error / Math.abs(expected));
            });
        }
        
        /**
         * Relative error scorer.
         */
        public static <I> Scorer<I, Number> relativeError(java.util.function.Function<I, Number> expectedFunc, double maxError) {
            return of("relative_error", (input, output) -> {
                if (output == null) return 0.0;
                
                var expected = expectedFunc.apply(input).doubleValue();
                var actual = output.doubleValue();
                
                if (expected == 0) {
                    return actual == 0 ? 1.0 : 0.0;
                }
                
                var relError = Math.abs((expected - actual) / expected);
                return Math.max(0.0, 1.0 - relError / maxError);
            });
        }
    }
    
    /**
     * Combines multiple scorers into one.
     */
    static <I, O> Scorer<I, O> combine(String name, java.util.List<Scorer<I, O>> scorers, 
                                       java.util.function.Function<java.util.List<Double>, Double> combiner) {
        return new Scorer<I, O>() {
            @Override
            public double score(I input, O output) {
                var scores = scorers.stream()
                    .map(scorer -> scorer.score(input, output))
                    .toList();
                return combiner.apply(scores);
            }
            
            @Override
            public String name() {
                return name;
            }
        };
    }
    
    /**
     * Creates a weighted average scorer.
     */
    static <I, O> Scorer<I, O> weightedAverage(java.util.Map<Scorer<I, O>, Double> scorerWeights) {
        return new Scorer<I, O>() {
            @Override
            public double score(I input, O output) {
                var totalWeight = scorerWeights.values().stream()
                    .mapToDouble(Double::doubleValue)
                    .sum();
                
                return scorerWeights.entrySet().stream()
                    .mapToDouble(entry -> entry.getKey().score(input, output) * entry.getValue())
                    .sum() / totalWeight;
            }
            
            @Override
            public String name() {
                return "weighted_average";
            }
        };
    }
}