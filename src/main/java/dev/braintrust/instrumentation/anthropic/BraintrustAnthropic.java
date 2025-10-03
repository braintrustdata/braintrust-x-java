package dev.braintrust.instrumentation.anthropic;

import com.anthropic.client.AnthropicClient;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.*;

/** TODO: document */
public final class BraintrustAnthropic {

    public static AnthropicClient wrap(OpenTelemetry otel, AnthropicClient client) {
        throw new RuntimeException("TODO");
    }
}
