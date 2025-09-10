package dev.braintrust.claude.trace;

import static org.assertj.core.api.Assertions.*;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.Test;

class BraintrustContextTest {

    @Test
    void testForProject() {
        // When
        var context = BraintrustContext.forProject("project-123");

        // Then
        assertThat(context.projectId()).hasValue("project-123");
        assertThat(context.experimentId()).isEmpty();
        assertThat(context.parentType()).hasValue("project");
    }

    @Test
    void testForExperiment() {
        // When
        var context = BraintrustContext.forExperiment("exp-456");

        // Then
        assertThat(context.projectId()).isEmpty();
        assertThat(context.experimentId()).hasValue("exp-456");
        assertThat(context.parentType()).hasValue("experiment");
    }

    @Test
    void testStoreInContext() {
        // Given
        var btContext = BraintrustContext.forProject("project-789");
        var otelContext = Context.root();

        // When
        var newContext = btContext.storeInContext(otelContext);

        // Then
        var retrieved = BraintrustContext.fromContext(newContext);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.projectId()).hasValue("project-789");
    }

    @Test
    void testFromContextNotPresent() {
        // Given
        var context = Context.root();

        // When
        var btContext = BraintrustContext.fromContext(context);

        // Then
        assertThat(btContext).isNull();
    }

    @Test
    void testCurrent() {
        // Given - no context set
        assertThat(BraintrustContext.current()).isNull();

        // When - set context
        var btContext = BraintrustContext.forExperiment("exp-current");
        var context = btContext.storeInContext(Context.current());

        try (Scope scope = context.makeCurrent()) {
            // Then
            var current = BraintrustContext.current();
            assertThat(current).isNotNull();
            assertThat(current.experimentId()).hasValue("exp-current");
        }

        // After scope closes
        assertThat(BraintrustContext.current()).isNull();
    }

    @Test
    void testBuilder() {
        // When
        var context = BraintrustContext.builder().projectId("project-builder").build();

        // Then
        assertThat(context.projectId()).hasValue("project-builder");
        assertThat(context.experimentId()).isEmpty();
        assertThat(context.parentType()).hasValue("project");
    }

    @Test
    void testBuilderExperiment() {
        // When
        var context = BraintrustContext.builder().experimentId("exp-builder").build();

        // Then
        assertThat(context.projectId()).isEmpty();
        assertThat(context.experimentId()).hasValue("exp-builder");
        assertThat(context.parentType()).hasValue("experiment");
    }

    @Test
    void testBuilderOverwritesProject() {
        // When - experiment overwrites project
        var context =
                BraintrustContext.builder()
                        .projectId("project-first")
                        .experimentId("exp-second")
                        .build();

        // Then - experiment takes precedence
        assertThat(context.projectId()).isEmpty();
        assertThat(context.experimentId()).hasValue("exp-second");
        assertThat(context.parentType()).hasValue("experiment");
    }

    @Test
    void testBuilderStoreInContext() {
        // When
        var newContext =
                BraintrustContext.builder()
                        .projectId("project-store")
                        .storeInContext(Context.root());

        // Then
        var retrieved = BraintrustContext.fromContext(newContext);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.projectId()).hasValue("project-store");
    }

    @Test
    void testBuilderStoreInCurrent() {
        // Given
        var originalContext = Context.current();

        // When
        var newContext = BraintrustContext.builder().experimentId("exp-current").storeInCurrent();

        try (Scope scope = newContext.makeCurrent()) {
            // Then
            var current = BraintrustContext.current();
            assertThat(current).isNotNull();
            assertThat(current.experimentId()).hasValue("exp-current");
        }
    }

    @Test
    void testContextPropagation() {
        // Given
        var parent = BraintrustContext.forProject("parent-project");
        var parentContext = parent.storeInContext(Context.root());

        // When - create child context
        var child = BraintrustContext.forExperiment("child-exp");
        var childContext = child.storeInContext(parentContext);

        // Then - child context overwrites parent
        var retrieved = BraintrustContext.fromContext(childContext);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.projectId()).isEmpty();
        assertThat(retrieved.experimentId()).hasValue("child-exp");

        // Parent context is not affected
        var parentRetrieved = BraintrustContext.fromContext(parentContext);
        assertThat(parentRetrieved).isNotNull();
        assertThat(parentRetrieved.projectId()).hasValue("parent-project");
    }

    @Test
    void testNullValues() {
        // When
        var context = new BraintrustContext.Builder().projectId(null).build();

        // Then
        assertThat(context.projectId()).isEmpty();
        assertThat(context.experimentId()).isEmpty();
        assertThat(context.parentType()).isEmpty();
    }
}
