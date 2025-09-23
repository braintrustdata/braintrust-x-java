package dev.braintrust.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BraintrustConfigTest {
    @Test
    void throwsWithoutRequiredFields() {
        assertThrows(
                Exception.class, // don't care
                () -> BraintrustConfig.builder().apiKey(null).build(),
                "API key required");

        assertThrows(
                Exception.class, // don't care
                () ->
                        BraintrustConfig.builder()
                                .apiKey("foo")
                                .defaultProjectId(null)
                                .defaultProjectName("")
                                .build(),
                "Project info required");
        assertDoesNotThrow(
                () -> BraintrustConfig.builder().apiKey("foobar").build(),
                "Only an API key is required");
    }
}
