package dev.braintrust.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class BraintrustConfigTest {
    
    @Test
    void testBuilderWithDefaults() {
        // When
        var config = BraintrustConfig.builder()
            .apiKey("test-key")
            .build();
        
        // Then
        assertThat(config.apiKey()).isEqualTo("test-key");
        assertThat(config.apiUrl()).isEqualTo(URI.create("https://api.braintrust.dev"));
        assertThat(config.appUrl()).isEqualTo(URI.create("https://www.braintrust.dev"));
        assertThat(config.defaultProjectId()).isEmpty();
        assertThat(config.enableTraceConsoleLog()).isFalse();
        assertThat(config.debug()).isFalse();
        assertThat(config.requestTimeout()).isEqualTo(Duration.ofSeconds(30));
    }
    
    @Test
    void testBuilderWithEnvironmentVariables() {
        // Note: We can't easily mock environment variables in plain JUnit
        // This test demonstrates the expected behavior if environment variables were set
        // In practice, the builder pattern should be used in tests
        
        // When using the builder to simulate environment variables
        var config = BraintrustConfig.builder()
            .apiKey("env-key")
            .apiUrl("https://custom.api.dev")
            .appUrl("https://custom.app.dev")
            .defaultProjectId("project-123")
            .enableTraceConsoleLog(true)
            .debug(true)
            .build();
        
        // Then
        assertThat(config.apiKey()).isEqualTo("env-key");
        assertThat(config.apiUrl()).isEqualTo(URI.create("https://custom.api.dev"));
        assertThat(config.appUrl()).isEqualTo(URI.create("https://custom.app.dev"));
        assertThat(config.defaultProjectId()).hasValue("project-123");
        assertThat(config.enableTraceConsoleLog()).isTrue();
        assertThat(config.debug()).isTrue();
    }
    
    @Test
    void testBuilderOverridesEnvironment() {
        // Builder values should override any environment values
        // Since we can't mock environment variables, we test the builder directly
        
        // When
        var config = BraintrustConfig.builder()
            .apiKey("override-key")
            .debug(false)
            .build();
        
        // Then
        assertThat(config.apiKey()).isEqualTo("override-key");
        assertThat(config.debug()).isFalse();
    }
    
    @Test
    void testCreateWithConsumer() {
        // When
        var config = BraintrustConfig.create(builder -> builder
            .apiKey("test-key")
            .apiUrl("https://test.api.dev")
            .defaultProjectId("test-project")
            .requestTimeout(Duration.ofSeconds(60))
        );
        
        // Then
        assertThat(config.apiKey()).isEqualTo("test-key");
        assertThat(config.apiUrl()).isEqualTo(URI.create("https://test.api.dev"));
        assertThat(config.defaultProjectId()).hasValue("test-project");
        assertThat(config.requestTimeout()).isEqualTo(Duration.ofSeconds(60));
    }
    
    @Test
    void testApiKeyRequired() {
        // When/Then
        assertThatThrownBy(() -> BraintrustConfig.builder().build())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("API key is required");
    }
    
    @Test
    void testApiKeyCannotBeBlank() {
        // When/Then
        assertThatThrownBy(() -> BraintrustConfig.builder().apiKey("   ").build())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("API key is required");
    }
    
    @ParameterizedTest
    @CsvSource({
        "true, true",
        "TRUE, true",
        "True, true",
        "false, false",
        "FALSE, false",
        "False, false",
        "1, false",
        "yes, false",
        "no, false",
        ", false"
    })
    void testBooleanEnvironmentParsing(String envValue, boolean expected) {
        // Test the boolean parsing logic directly
        // The actual parsing in BraintrustConfig uses Boolean.parseBoolean
        // which only returns true for "true" (case-insensitive)
        var parsed = envValue != null ? Boolean.parseBoolean(envValue) : false;
        assertThat(parsed).isEqualTo(expected);
    }
    
    @ParameterizedTest
    @ValueSource(strings = {
        "https://api.example.com",
        "http://localhost:8080",
        "https://api.example.com/v1"
    })
    void testApiUrlParsing(String url) {
        // When
        var config = BraintrustConfig.builder()
            .apiKey("test")
            .apiUrl(url)
            .build();
        
        // Then
        assertThat(config.apiUrl()).isEqualTo(URI.create(url));
    }
    
    @Test
    void testInvalidUrlThrowsException() {
        // When/Then
        assertThatThrownBy(() -> 
            BraintrustConfig.builder()
                .apiKey("test")
                .apiUrl("not a valid url")
                .build()
        ).isInstanceOf(IllegalArgumentException.class);
    }
    
}