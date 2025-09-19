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
    private static final String DEFAULT_PROJECT_NAME = "default-java-project";
    private static final String DEFAULT_API_URL = "https://api.braintrust.dev";
    private static final String DEFAULT_APP_URL = "https://www.braintrust.dev";

    private final String apiKey;
    private final URI apiUrl;
    private final String tracesPath;
    private final String logsPath;
    private final URI appUrl;
    @Nullable private final String defaultProjectId;
    @Nullable private final String defaultProjectName;
    private final boolean enableTraceConsoleLog;
    private final boolean debug;
    private final Duration requestTimeout;
    private final boolean experimentalOtelLogs;

    private BraintrustConfig(Builder builder) {
        this.apiKey = builder.apiKey;
        this.apiUrl = builder.apiUrl;
        this.tracesPath = builder.tracesPath;
        this.logsPath = builder.logsPath;
        this.appUrl = builder.appUrl;
        this.experimentalOtelLogs = builder.experimentalOtelLogs;
        this.defaultProjectId = builder.defaultProjectId;
        this.defaultProjectName = builder.defaultProjectName;
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

    public String tracesPath() {
        return tracesPath;
    }

    public String logsPath() {
        return logsPath;
    }

    public URI appUrl() {
        return appUrl;
    }

    public Optional<String> defaultProjectId() {
        return Optional.ofNullable(defaultProjectId);
    }

    public Optional<String> defaultProjectName() {
        return Optional.ofNullable(defaultProjectName);
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

    /**
     * The parent attribute tells braintrust where to send otel data
     * <br/><br/>
     * The otel ingestion endpoint looks for (a) braintrust.parent = project_id|project_name|experiment_id:value otel attribute and routes accordingly
     * <br/><br/>
     * (b) if a span has no parent marked explicitly, it will look to see if there's an x-bt-parent http header (with the same format marked above e.g. project_name:andrew) that parent will apply to all spans in a request that don't have one
     * <br/><br/>
     * If neither (a) nor (b) exists, the data is dropped
     */
    public Optional<String> getBraintrustParentValue() {
        if (null != defaultProjectId) {
            return Optional.of("project_id:" + defaultProjectId);
        } else if (null != defaultProjectName) {
            return Optional.of("project_name:" + defaultProjectName);
        } else {
            return Optional.empty();
        }
    }

    /** Creates a new builder initialized with environment variables. */
    public static Builder builder() {
        return new Builder();
    }

    /** Creates a config from environment variables and retrieves organization name from API. */
    public static BraintrustConfig fromEnvironment() {
        return builder().build();
    }

    /**
     * Creates a config with a consumer for customization and retrieves organization name from API.
     */
    public static BraintrustConfig create(Consumer<Builder> customizer) {
        var builder = builder();
        customizer.accept(builder);
        return builder.build();
    }

    public static final class Builder {
        private String apiKey;
        private URI apiUrl;
        private String tracesPath;
        private String logsPath;
        private URI appUrl;
        private String defaultProjectId;
        private String defaultProjectName;
        private boolean enableTraceConsoleLog;
        private boolean debug;
        private boolean experimentalOtelLogs;
        private Duration requestTimeout = Duration.ofSeconds(30);

        private Builder() {
            this.apiKey = getEnv("BRAINTRUST_API_KEY", null);
            this.apiUrl = URI.create(getEnv("BRAINTRUST_API_URL", DEFAULT_API_URL));
            this.tracesPath = getEnv("BRAINTRUST_TRACES_PATH", "/otel/v1/traces");
            this.logsPath = getEnv("BRAINTRUST_LOGS_PATH", "/otel/v1/logs");
            this.appUrl = URI.create(getEnv("BRAINTRUST_APP_URL", DEFAULT_APP_URL));
            this.defaultProjectId = getEnv("BRAINTRUST_DEFAULT_PROJECT_ID", null);
            this.defaultProjectName = getEnv("BRAINTRUST_DEFAULT_PROJECT", DEFAULT_PROJECT_NAME).trim();
            if ((null == defaultProjectId || "".equalsIgnoreCase(defaultProjectId.trim()))
                    && "".equalsIgnoreCase(defaultProjectName)) {
                // NOTE: this should not happen,
                // but if someone happens to export their default project to the empty string and does not set a default project ID we don't have a valid parent for otel data.
                throw new RuntimeException("Missing required envars. Please export BRAINTRUST_DEFAULT_PROJECT_ID or BRAINTRUST_DEFAULT_PROJECT");
            }
            this.enableTraceConsoleLog =
                    Boolean.parseBoolean(getEnv("BRAINTRUST_ENABLE_TRACE_CONSOLE_LOG", "false"));
            this.debug = Boolean.parseBoolean(getEnv("BRAINTRUST_DEBUG", "false"));
            this.experimentalOtelLogs = Boolean.parseBoolean(getEnv("BRAINTRUST_X_OTEL_LOGS", "false"));
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
                throw new IllegalStateException("API key is required. Set BRAINTRUST_API_KEY environment variable or use apiKey() method.");
            }

            return new BraintrustConfig(this);
        }

        private static String getEnv(String key, String defaultValue) {
            String value = System.getenv(key);
            // Trim any whitespace that might have been accidentally included
            return value != null ? value.trim() : defaultValue;
        }
    }
}
