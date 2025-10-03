package dev.braintrust.instrumentation.anthropic.otel;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.common.AttributeKey;

// copied from GenAiIncubatingAttributes
final class GenAiAttributes {
    static final AttributeKey<String> GEN_AI_PROVIDER_NAME = stringKey("gen_ai.provider.name");

    static final class GenAiOperationNameIncubatingValues {
        static final String CHAT = "chat";

        private GenAiOperationNameIncubatingValues() {}
    }

    static final class GenAiProviderNameIncubatingValues {
        static final String ANTHROPIC = "anthropic";

        private GenAiProviderNameIncubatingValues() {}
    }

    private GenAiAttributes() {}
}
