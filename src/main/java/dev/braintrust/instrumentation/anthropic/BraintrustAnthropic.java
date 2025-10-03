package dev.braintrust.instrumentation.anthropic;

import com.anthropic.client.AnthropicClient;
import dev.braintrust.instrumentation.anthropic.otel.AnthropicTelemetry;
import io.opentelemetry.api.OpenTelemetry;

/** Braintrust Anthropic client instrumentation. */
public final class BraintrustAnthropic {

    /** Instrument Anthropic client with braintrust traces */
    public static AnthropicClient wrap(OpenTelemetry otel, AnthropicClient client) {
        return AnthropicTelemetry.builder(otel).setCaptureMessageContent(true).build().wrap(client);
    }
}
