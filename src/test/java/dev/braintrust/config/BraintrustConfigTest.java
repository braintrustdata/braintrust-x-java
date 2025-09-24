package dev.braintrust.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BraintrustConfigTest {
    @Test
    void parentLogic() {
        var defaultConfig = BraintrustConfig.of("BRAINTRUST_API_KEY", "foobar");
        assertTrue(
                defaultConfig.getBraintrustParentValue().isPresent(),
                "default config should have a parent");
    }
}
