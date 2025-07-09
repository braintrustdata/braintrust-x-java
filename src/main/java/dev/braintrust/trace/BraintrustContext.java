package dev.braintrust.trace;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Context propagation for Braintrust-specific attributes.
 * Uses OpenTelemetry's Context API for thread-safe propagation.
 */
public final class BraintrustContext {
    private static final ContextKey<BraintrustContext> KEY = ContextKey.named("braintrust-context");
    
    @Nullable
    private final String projectId;
    @Nullable
    private final String experimentId;
    @Nullable
    private final String parentType;
    
    private BraintrustContext(@Nullable String projectId, @Nullable String experimentId, @Nullable String parentType) {
        this.projectId = projectId;
        this.experimentId = experimentId;
        this.parentType = parentType;
    }
    
    /**
     * Creates a context for a project parent.
     */
    public static BraintrustContext forProject(String projectId) {
        return new BraintrustContext(projectId, null, "project");
    }
    
    /**
     * Creates a context for an experiment parent.
     */
    public static BraintrustContext forExperiment(String experimentId) {
        return new BraintrustContext(null, experimentId, "experiment");
    }
    
    /**
     * Stores this context in the given Context.
     */
    public Context storeInContext(Context context) {
        return context.with(KEY, this);
    }
    
    /**
     * Retrieves a BraintrustContext from the given Context.
     */
    @Nullable
    public static BraintrustContext fromContext(Context context) {
        return context.get(KEY);
    }
    
    /**
     * Returns the current BraintrustContext from the current Context.
     */
    @Nullable
    public static BraintrustContext current() {
        return fromContext(Context.current());
    }
    
    public Optional<String> projectId() {
        return Optional.ofNullable(projectId);
    }
    
    public Optional<String> experimentId() {
        return Optional.ofNullable(experimentId);
    }
    
    public Optional<String> parentType() {
        return Optional.ofNullable(parentType);
    }
    
    /**
     * Builder for creating contexts with multiple attributes.
     */
    public static final class Builder {
        private String projectId;
        private String experimentId;
        private String parentType;
        
        public Builder projectId(String projectId) {
            this.projectId = projectId;
            this.parentType = "project";
            return this;
        }
        
        public Builder experimentId(String experimentId) {
            this.experimentId = experimentId;
            this.parentType = "experiment";
            return this;
        }
        
        public BraintrustContext build() {
            return new BraintrustContext(projectId, experimentId, parentType);
        }
        
        public Context storeInContext(Context context) {
            return build().storeInContext(context);
        }
        
        public Context storeInCurrent() {
            return storeInContext(Context.current());
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
}