package dev.braintrust.instrumentation.anthropic.otel;

// copied from GenAiIncubatingAttributes
final class GenAiAttributes {
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
