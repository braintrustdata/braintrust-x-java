package dev.braintrust.trace;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import java.util.Optional;
import javax.annotation.Nullable;

/** Context propagation for Braintrust-specific attributes. */
public final class BraintrustContext {
    private static final ContextKey<BraintrustContext> KEY = ContextKey.named("braintrust-context");

    @Nullable private final String projectId;
    @Nullable private final String experimentId;
    @Nullable private final String parentType;

    private BraintrustContext(
            @Nullable String projectId,
            @Nullable String experimentId,
            @Nullable String parentType) {
        this.projectId = projectId;
        this.experimentId = experimentId;
        this.parentType = parentType;
    }

    /** Creates a context for an experiment parent. */
    public static Context of(String experimentId, Span span) {
        return Context.current()
                .with(span)
                .with(KEY, new BraintrustContext(null, experimentId, "experiment"));
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
