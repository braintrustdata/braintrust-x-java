package dev.braintrust.config;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/**
 * Configuration for Braintrust SDK. Uses builder pattern with sensible defaults and environment
 * variable support.
 */
public final class BraintrustConfig {
    private static final String DEFAULT_API_URL = "https://api.braintrust.dev";
    private static final String DEFAULT_APP_URL = "https://www.braintrust.dev";

    private final String apiKey;
    private final URI apiUrl;
    private final URI appUrl;
    @Nullable private final String defaultProjectId;
    private final boolean enableTraceConsoleLog;
    private final boolean debug;
    private final Duration requestTimeout;

    private BraintrustConfig(Builder builder) {
        this.apiKey = builder.apiKey;
        this.apiUrl = builder.apiUrl;
        this.appUrl = builder.appUrl;
        this.defaultProjectId = builder.defaultProjectId;
        this.enableTraceConsoleLog = builder.enableTraceConsoleLog;
        this.debug = builder.debug;
        this.requestTimeout = builder.requestTimeout;
    }

    public String apiKey() {
        return apiKey;
    }

    public URI apiUrl() {
        return apiUrl;
    }

    public URI appUrl() {
        return appUrl;
    }

    public Optional<String> defaultProjectId() {
        return Optional.ofNullable(defaultProjectId);
    }

    public boolean enableTraceConsoleLog() {
        return enableTraceConsoleLog;
    }

    public boolean debug() {
        return debug;
    }

    public Duration requestTimeout() {
        return requestTimeout;
    }

    /** Creates a new builder initialized with environment variables. */
    public static Builder builder() {
        return new Builder();
    }

    /** Creates a config from environment variables only. */
    public static BraintrustConfig fromEnvironment() {
        return builder().build();
    }

    /** Creates a config with a consumer for customization. */
    public static BraintrustConfig create(Consumer<Builder> customizer) {
        var builder = builder();
        customizer.accept(builder);
        return builder.build();
    }

    public static final class Builder {
        private String apiKey;
        private URI apiUrl;
        private URI appUrl;
        private String defaultProjectId;
        private boolean enableTraceConsoleLog;
        private boolean debug;
        private Duration requestTimeout = Duration.ofSeconds(30);

        private Builder() {
            // Initialize from environment
            this.apiKey = getEnv("BRAINTRUST_API_KEY", null);
            this.apiUrl = URI.create(getEnv("BRAINTRUST_API_URL", DEFAULT_API_URL));
            this.appUrl = URI.create(getEnv("BRAINTRUST_APP_URL", DEFAULT_APP_URL));
            this.defaultProjectId = getEnv("BRAINTRUST_DEFAULT_PROJECT_ID", null);
            this.enableTraceConsoleLog =
                    Boolean.parseBoolean(getEnv("BRAINTRUST_ENABLE_TRACE_CONSOLE_LOG", "false"));
            this.debug = Boolean.parseBoolean(getEnv("BRAINTRUST_DEBUG", "false"));
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder apiUrl(String apiUrl) {
            return apiUrl(URI.create(apiUrl));
        }

        public Builder apiUrl(URI apiUrl) {
            this.apiUrl = apiUrl;
            return this;
        }

        public Builder appUrl(String appUrl) {
            return appUrl(URI.create(appUrl));
        }

        public Builder appUrl(URI appUrl) {
            this.appUrl = appUrl;
            return this;
        }

        public Builder defaultProjectId(String projectId) {
            this.defaultProjectId = projectId;
            return this;
        }

        public Builder enableTraceConsoleLog(boolean enable) {
            this.enableTraceConsoleLog = enable;
            return this;
        }

        public Builder debug(boolean debug) {
            this.debug = debug;
            return this;
        }

        public Builder requestTimeout(Duration timeout) {
            this.requestTimeout = timeout;
            return this;
        }

        public BraintrustConfig build() {
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalStateException(
                        "API key is required. Set BRAINTRUST_API_KEY environment variable or use"
                                + " apiKey() method.");
            }
            return new BraintrustConfig(this);
        }

        private static String getEnv(String key, String defaultValue) {
            String value = System.getenv(key);
            return value != null ? value : defaultValue;
        }
    }
}
