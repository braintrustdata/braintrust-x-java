package dev.braintrust.trace;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Used to identify the braintrust parent for spans and experiments. SDK users probably don't want
 * to use this and instead should use {@link BraintrustTracing} or {@link dev.braintrust.eval.Eval}
 */
public final class BraintrustContext {
    private static final ContextKey<BraintrustContext> KEY = ContextKey.named("braintrust-context");

    // NOTE we're actually not using this right now, but leaving in for the future
    @Nullable private final String projectId;
    @Nullable private final String experimentId;

    private BraintrustContext(@Nullable String projectId, @Nullable String experimentId) {
        this.projectId = projectId;
        this.experimentId = experimentId;
    }

    /** Creates a context for an experiment parent. */
    public static Context ofExperiment(@Nonnull String experimentId, @Nonnull Span span) {
        Objects.requireNonNull(experimentId);
        Objects.requireNonNull(span);
        return Context.current().with(span).with(KEY, new BraintrustContext(null, experimentId));
    }

    /** Retrieves a BraintrustContext from the given Context. */
    @Nullable
    public static BraintrustContext fromContext(Context context) {
        return context.get(KEY);
    }

    public Optional<String> projectId() {
        return Optional.ofNullable(projectId);
    }

    public Optional<String> experimentId() {
        return Optional.ofNullable(experimentId);
    }
}
