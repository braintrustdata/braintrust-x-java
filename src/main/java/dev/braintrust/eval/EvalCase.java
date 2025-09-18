package dev.braintrust.eval;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A single test case in an LLM eval.
 *
 * @param input test input
 * @param expected expected value
 * @param tags additional tags to apply in the braintrust UI
 * @param metadata additional key-value data to apply in the braintrust UI
 */
public record EvalCase<INPUT, OUTPUT>(INPUT input, OUTPUT expected, @Nonnull List<String> tags,
                                        @Nonnull Map<String, Object> metadata) {
    public EvalCase {
        if (!metadata.isEmpty()) {
            throw new RuntimeException("TODO: metadata support not yet implemented");
        }
        if (!tags.isEmpty()) {
            throw new RuntimeException("TODO: tags support not yet implemented");
        }
    }

    public static <INPUT, OUTPUT> EvalCase<INPUT, OUTPUT> of(INPUT input, OUTPUT expected) {
        return of(input, expected, List.of(), Map.of());
    }

    public static <INPUT, OUTPUT> EvalCase<INPUT, OUTPUT> of(INPUT input, OUTPUT expected, @Nonnull List<String> tags, @Nonnull Map<String, Object> metadata) {
        return new EvalCase<>(input, expected, tags, metadata);
    }

    public record Result<INPUT, OUTPUT>(EvalCase<INPUT, OUTPUT> evalCase, OUTPUT result) {}
}
