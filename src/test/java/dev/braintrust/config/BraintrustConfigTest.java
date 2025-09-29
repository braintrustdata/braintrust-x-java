package dev.braintrust.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BraintrustConfigTest {
    @Test
    void parentDefaultsToProjectName() {
        var defaultConfig =
                BraintrustConfig.of(
                        "BRAINTRUST_API_KEY",
                        "foobar",
                        "BRAINTRUST_DEFAULT_PROJECT_NAME",
                        "proj-name");
        assertEquals(
                "project_name:proj-name", defaultConfig.getBraintrustParentValue().orElseThrow());
    }

    @Test
    void parentUsesProjectId() {
        var defaultConfig =
                BraintrustConfig.of(
                        "BRAINTRUST_API_KEY", "foobar",
                        "BRAINTRUST_DEFAULT_PROJECT_NAME", "proj-name",
                        "BRAINTRUST_DEFAULT_PROJECT_ID", "12345");
        assertEquals(
                "project_id:" + defaultConfig.defaultProjectId().orElseThrow(),
                defaultConfig.getBraintrustParentValue().orElseThrow());
    }

    @Test
    void projectUriFetching() {
        var projectName = "some project";
        var projectId = "123456";
        var orgInfo =
                new dev.braintrust.api.BraintrustApiClient.OrganizationInfo("org_123", "Test Org");
        var project =
                new dev.braintrust.api.BraintrustApiClient.Project(
                        projectId,
                        projectName,
                        "org_123",
                        "2023-01-01T00:00:00Z",
                        "2023-01-01T00:00:00Z");
        var orgAndProjectInfo =
                new dev.braintrust.api.BraintrustApiClient.OrganizationAndProjectInfo(
                        orgInfo, project);
        var client = new dev.braintrust.api.BraintrustApiClient.InMemoryImpl(orgAndProjectInfo);
        var config =
                BraintrustConfig.of(
                        "BRAINTRUST_API_KEY", "foobar", "BRAINTRUST_DEFAULT_PROJECT_ID", projectId);
        assertEquals(
                "https://www.braintrust.dev/app/Test%20Org/p/some%20project",
                config.fetchProjectURI(client).toASCIIString());
    }
}
