package dev.braintrust.eval;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ScorerTest {

    @Test
    void testCustomScorer() {
        // Given
        Scorer<String, Integer> lengthScorer =
                new Scorer<>() {
                    @Override
                    public double score(String input, Integer output) {
                        return output == input.length() ? 1.0 : 0.0;
                    }

                    @Override
                    public String name() {
                        return "length_scorer";
                    }
                };

        // When/Then
        assertThat(lengthScorer.score("hello", 5)).isEqualTo(1.0);
        assertThat(lengthScorer.score("hello", 4)).isEqualTo(0.0);
        assertThat(lengthScorer.name()).isEqualTo("length_scorer");
    }

    @Test
    void testScorerFactoryMethod() {
        // Given
        var scorer =
                Scorer.of(
                        "custom",
                        (String input, String output) ->
                                input.equalsIgnoreCase(output) ? 1.0 : 0.0);

        // When/Then
        assertThat(scorer.score("Hello", "hello")).isEqualTo(1.0);
        assertThat(scorer.score("Hello", "world")).isEqualTo(0.0);
        assertThat(scorer.name()).isEqualTo("custom");
    }

    @ParameterizedTest
    @CsvSource({
        "hello, hello, 1.0",
        "hello, Hello, 0.0",
        "world, world, 1.0",
        "test, testing, 0.0"
    })
    void testExactMatchScorer(String expected, String actual, double expectedScore) {
        // Given
        var scorer = Scorer.StringScorers.exactMatch((String input) -> expected);

        // When/Then
        assertThat(scorer.score(null, actual)).isEqualTo(expectedScore);
    }

    @ParameterizedTest
    @CsvSource({
        "hello, hello, 1.0", // Exact match
        "hello, hallo, 0.8", // 1 substitution
        "hello, helo, 0.8", // 1 deletion
        "hello, helloo, 0.833", // 1 insertion
        "abc, xyz, 0.0", // Completely different
        "test, , 0.0", // Empty output
        ", test, 0.0" // Empty expected
    })
    void testLevenshteinScorer(String expected, String actual, double expectedScore) {
        // Given
        var scorer = Scorer.StringScorers.levenshtein((String input) -> expected);

        // When
        var score = scorer.score(null, actual);

        // Then
        assertThat(score).isCloseTo(expectedScore, within(0.001));
    }

    @Test
    void testContainsScorer() {
        // Given
        var scorer = Scorer.StringScorers.contains((String input) -> "keyword");

        // When/Then
        assertThat(scorer.score(null, "This contains keyword here")).isEqualTo(1.0);
        assertThat(scorer.score(null, "This does not contain it")).isEqualTo(0.0);
        assertThat(scorer.score(null, "KEYWORD in caps")).isEqualTo(0.0); // Case sensitive
        assertThat(scorer.score(null, null)).isEqualTo(0.0);
    }

    @Test
    void testRegexScorer() {
        // Given
        var scorer = Scorer.StringScorers.regex((String input) -> Pattern.compile("\\d{3}-\\d{4}"));

        // When/Then
        assertThat(scorer.score(null, "123-4567")).isEqualTo(1.0);
        assertThat(scorer.score(null, "Call me at 555-1234"))
                .isEqualTo(0.0); // Must match entire string
        assertThat(scorer.score(null, "12-3456")).isEqualTo(0.0);
        assertThat(scorer.score(null, null)).isEqualTo(0.0);
    }

    @Test
    void testAbsoluteErrorScorer() {
        // Given
        var scorer =
                Scorer.NumericScorers.absoluteError(
                        (String input) -> 100.0, 10.0 // threshold
                        );

        // When/Then
        assertThat(scorer.score(null, 100.0)).isEqualTo(1.0); // Exact match
        assertThat(scorer.score(null, 95.0)).isEqualTo(1.0); // Within threshold
        assertThat(scorer.score(null, 110.0)).isEqualTo(1.0); // Within threshold
        assertThat(scorer.score(null, 120.0))
                .isCloseTo(0.8, within(0.01)); // Normalized by expected
        assertThat(scorer.score(null, 200.0)).isEqualTo(0.0); // Too far
        assertThat(scorer.score(null, null)).isEqualTo(0.0); // Null handling
    }

    @Test
    void testRelativeErrorScorer() {
        // Given
        var scorer =
                Scorer.NumericScorers.relativeError(
                        (String input) -> 100.0, 0.1 // max 10% error
                        );

        // When/Then
        assertThat(scorer.score(null, 100.0)).isEqualTo(1.0); // Exact
        assertThat(scorer.score(null, 105.0)).isEqualTo(0.5); // 5% error = 50% score
        assertThat(scorer.score(null, 110.0)).isEqualTo(0.0); // 10% error = 0% score
        assertThat(scorer.score(null, 90.0)).isEqualTo(0.0); // -10% error = 0% score

        // Test zero expected value
        var zeroScorer = Scorer.NumericScorers.relativeError((String input) -> 0.0, 0.1);
        assertThat(zeroScorer.score(null, 0.0)).isEqualTo(1.0);
        assertThat(zeroScorer.score(null, 1.0)).isEqualTo(0.0);
    }

    @Test
    void testCombinedScorer() {
        // Given
        var scorer1 = Scorer.of("scorer1", (String in, String out) -> 0.8);
        var scorer2 = Scorer.of("scorer2", (String in, String out) -> 0.6);
        var scorer3 = Scorer.of("scorer3", (String in, String out) -> 1.0);

        var avgScorer =
                Scorer.combine(
                        "average",
                        List.of(scorer1, scorer2, scorer3),
                        scores ->
                                scores.stream()
                                        .mapToDouble(Double::doubleValue)
                                        .average()
                                        .orElse(0.0));

        // When/Then
        assertThat(avgScorer.score("input", "output")).isCloseTo(0.8, within(0.001));
        assertThat(avgScorer.name()).isEqualTo("average");
    }

    @Test
    void testWeightedAverageScorer() {
        // Given
        var accuracyScorer = Scorer.of("accuracy", (String in, String out) -> 0.9);
        var speedScorer = Scorer.of("speed", (String in, String out) -> 0.6);
        var costScorer = Scorer.of("cost", (String in, String out) -> 0.8);

        var weightedScorer =
                Scorer.weightedAverage(
                        Map.of(
                                accuracyScorer, 0.5, // 50% weight
                                speedScorer, 0.3, // 30% weight
                                costScorer, 0.2 // 20% weight
                                ));

        // When
        var score = weightedScorer.score("input", "output");

        // Then
        // (0.9 * 0.5) + (0.6 * 0.3) + (0.8 * 0.2) = 0.45 + 0.18 + 0.16 = 0.79
        assertThat(score).isCloseTo(0.79, within(0.001));
        assertThat(weightedScorer.name()).isEqualTo("weighted_average");
    }

    @Test
    void testLevenshteinDistanceCalculation() {
        // Test various edit distance scenarios
        record TestCase(String s1, String s2, int expectedDistance) {}

        var testCases =
                List.of(
                        new TestCase("", "", 0),
                        new TestCase("a", "", 1),
                        new TestCase("", "a", 1),
                        new TestCase("abc", "abc", 0),
                        new TestCase("abc", "abd", 1), // substitution
                        new TestCase("abc", "ab", 1), // deletion
                        new TestCase("abc", "abcd", 1), // insertion
                        new TestCase("kitten", "sitting", 3), // classic example
                        new TestCase("saturday", "sunday", 3));

        for (var testCase : testCases) {
            var scorer = Scorer.StringScorers.levenshtein(input -> testCase.s1);
            var score = scorer.score(null, testCase.s2);
            var maxLen = Math.max(testCase.s1.length(), testCase.s2.length());
            var expectedScore =
                    maxLen == 0 ? 1.0 : 1.0 - (double) testCase.expectedDistance / maxLen;

            assertThat(score)
                    .as("Levenshtein score for '%s' vs '%s'", testCase.s1, testCase.s2)
                    .isCloseTo(expectedScore, within(0.001));
        }
    }
}
