package dev.braintrust.test.otel;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.assertj.core.api.AbstractAssert;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AssertJ assertions for OpenTelemetry spans.
 * Provides fluent API for testing span attributes, events, and timing.
 */
public class SpanAssertions extends AbstractAssert<SpanAssertions, SpanData> {
    
    protected SpanAssertions(SpanData span) {
        super(span, SpanAssertions.class);
    }
    
    public static SpanAssertions assertThat(SpanData span) {
        return new SpanAssertions(span);
    }
    
    public SpanAssertions hasName(String name) {
        isNotNull();
        if (!actual.getName().equals(name)) {
            failWithMessage("Expected span name to be <%s> but was <%s>", name, actual.getName());
        }
        return this;
    }
    
    public SpanAssertions hasKind(SpanKind kind) {
        isNotNull();
        if (!actual.getKind().equals(kind)) {
            failWithMessage("Expected span kind to be <%s> but was <%s>", kind, actual.getKind());
        }
        return this;
    }
    
    public SpanAssertions hasStatus(StatusCode code) {
        isNotNull();
        if (!actual.getStatus().getStatusCode().equals(code)) {
            failWithMessage("Expected status code to be <%s> but was <%s>", 
                code, actual.getStatus().getStatusCode());
        }
        return this;
    }
    
    public SpanAssertions hasStatusWithDescription(StatusCode code, String description) {
        hasStatus(code);
        if (!actual.getStatus().getDescription().equals(description)) {
            failWithMessage("Expected status description to be <%s> but was <%s>", 
                description, actual.getStatus().getDescription());
        }
        return this;
    }
    
    public <T> SpanAssertions hasAttribute(AttributeKey<T> key, T value) {
        isNotNull();
        T actualValue = actual.getAttributes().get(key);
        if (!value.equals(actualValue)) {
            failWithMessage("Expected attribute <%s> to be <%s> but was <%s>", 
                key, value, actualValue);
        }
        return this;
    }
    
    public SpanAssertions hasAttributeKey(AttributeKey<?> key) {
        isNotNull();
        if (!actual.getAttributes().asMap().containsKey(key)) {
            failWithMessage("Expected span to have attribute key <%s>", key);
        }
        return this;
    }
    
    public SpanAssertions doesNotHaveAttributeKey(AttributeKey<?> key) {
        isNotNull();
        if (actual.getAttributes().asMap().containsKey(key)) {
            failWithMessage("Expected span not to have attribute key <%s>", key);
        }
        return this;
    }
    
    public SpanAssertions hasAttributeCount(int count) {
        isNotNull();
        int actualCount = actual.getAttributes().size();
        if (actualCount != count) {
            failWithMessage("Expected span to have <%d> attributes but had <%d>", 
                count, actualCount);
        }
        return this;
    }
    
    public SpanAssertions hasEventCount(int count) {
        isNotNull();
        int actualCount = actual.getEvents().size();
        if (actualCount != count) {
            failWithMessage("Expected span to have <%d> events but had <%d>", 
                count, actualCount);
        }
        return this;
    }
    
    public SpanAssertions hasEvent(String name) {
        isNotNull();
        boolean found = actual.getEvents().stream()
            .anyMatch(event -> event.getName().equals(name));
        if (!found) {
            failWithMessage("Expected span to have event named <%s>", name);
        }
        return this;
    }
    
    public SpanAssertions hasExceptionEvent() {
        return hasEvent("exception");
    }
    
    public SpanAssertions hasExceptionEventWithType(Class<? extends Throwable> exceptionType) {
        hasExceptionEvent();
        
        var exceptionEvents = actual.getEvents().stream()
            .filter(e -> e.getName().equals("exception"))
            .collect(Collectors.toList());
        
        boolean found = exceptionEvents.stream()
            .anyMatch(event -> {
                var type = event.getAttributes()
                    .get(AttributeKey.stringKey("exception.type"));
                return type != null && type.equals(exceptionType.getName());
            });
        
        if (!found) {
            failWithMessage("Expected span to have exception event of type <%s>", 
                exceptionType.getName());
        }
        return this;
    }
    
    public SpanAssertions hasDurationBetween(Duration min, Duration max) {
        isNotNull();
        var duration = Duration.ofNanos(actual.getEndEpochNanos() - actual.getStartEpochNanos());
        if (duration.compareTo(min) < 0 || duration.compareTo(max) > 0) {
            failWithMessage("Expected span duration to be between <%s> and <%s> but was <%s>", 
                min, max, duration);
        }
        return this;
    }
    
    public SpanAssertions startedAfter(Instant time) {
        isNotNull();
        var startTime = Instant.ofEpochSecond(0, actual.getStartEpochNanos());
        if (!startTime.isAfter(time)) {
            failWithMessage("Expected span to start after <%s> but started at <%s>", 
                time, startTime);
        }
        return this;
    }
    
    public SpanAssertions endedBefore(Instant time) {
        isNotNull();
        var endTime = Instant.ofEpochSecond(0, actual.getEndEpochNanos());
        if (!endTime.isBefore(time)) {
            failWithMessage("Expected span to end before <%s> but ended at <%s>", 
                time, endTime);
        }
        return this;
    }
    
    public SpanAssertions hasParentSpanId(String parentSpanId) {
        isNotNull();
        if (!actual.getParentSpanId().equals(parentSpanId)) {
            failWithMessage("Expected parent span ID to be <%s> but was <%s>", 
                parentSpanId, actual.getParentSpanId());
        }
        return this;
    }
    
    public SpanAssertions hasNoParent() {
        isNotNull();
        if (!actual.getParentSpanId().isEmpty()) {
            failWithMessage("Expected span to have no parent but had parent ID <%s>", 
                actual.getParentSpanId());
        }
        return this;
    }
    
    /**
     * Asserts that the span has all the specified attributes with their values.
     */
    public SpanAssertions hasAttributes(Map<AttributeKey<?>, Object> expectedAttributes) {
        isNotNull();
        for (Map.Entry<AttributeKey<?>, Object> entry : expectedAttributes.entrySet()) {
            @SuppressWarnings("unchecked")
            AttributeKey<Object> key = (AttributeKey<Object>) entry.getKey();
            hasAttribute(key, entry.getValue());
        }
        return this;
    }
    
    /**
     * Asserts that the span has ended.
     */
    public SpanAssertions hasEnded() {
        isNotNull();
        if (!actual.hasEnded()) {
            failWithMessage("Expected span to have ended but it hasn't");
        }
        return this;
    }
    
    /**
     * Asserts that the span has not ended.
     */
    public SpanAssertions hasNotEnded() {
        isNotNull();
        if (actual.hasEnded()) {
            failWithMessage("Expected span not to have ended but it has");
        }
        return this;
    }
}