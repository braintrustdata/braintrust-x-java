# Braintrust SDK Implementation Notes

This file contains important implementation details and quirks specific to the Braintrust API that should be considered when working on this SDK.

## API Quirks

### 1. Authorization Header Error Messages
**Symptom**: Getting error "Invalid key=value pair (missing equal-sign) in Authorization header"
**Cause**: This error is misleading - it usually means you're hitting a URL path that doesn't exist (404)
**Solution**: Verify the API endpoint path is correct

**Important**: This is a Braintrust API quirk where 404 errors are returned as 403 errors with this specific message

### 2. OTLP Endpoint Path
- Correct path: `/otel/v1/traces` (NOT `/otlp/v1/traces`)
- The Java OTLP exporter requires the full path in setEndpoint()
- Example: `setEndpoint(config.apiUrl() + "/otel/v1/traces")`

### 3. x-bt-parent Header (CRITICAL)
- **Required**: The `x-bt-parent` HTTP header must be set for traces to appear in experiments
- Format: `experiment_id:<id>` or `project_id:<id>` or `project_name:<name>`
- This header tells Braintrust which experiment/project the traces belong to
- Without this header, traces will be created but won't show up in the experiment view
- Implemented via custom BraintrustSpanExporter that dynamically sets the header

### 4. API Key Format
- Standard Bearer token format works: `Authorization: Bearer <api-key>`
- API keys work for both production (api.braintrust.dev) and staging (staging-api.braintrust.dev)

### 5. Transport Protocol
- Braintrust supports HTTP only (NOT gRPC)
- Both protobuf and JSON formats work over HTTP
- Use `OtlpHttpSpanExporter` not `OtlpGrpcSpanExporter`

### 6. REST API Endpoints (IMPORTANT)
The API uses **singular** resource names with `/v1/` prefix:
- Projects: `/v1/project` (NOT `/v1/projects` or `/projects`)
- Experiments: `/v1/experiment` (NOT `/v1/experiments` or `/experiments`)
- Datasets: `/v1/dataset` (NOT `/v1/datasets` or `/datasets`)
- Dataset operations: `/v1/dataset/{id}/insert`, `/v1/dataset/{id}/fetch`

**Note**: Using the wrong endpoint (e.g., `/projects` instead of `/v1/project`) will return a 403 error with the misleading "Invalid key=value pair" message.

## SDK Design Decisions

### 1. Mimic Go SDK
The goal is to match the Go SDK's developer experience:
- `RegisterProject()` and `RegisterExperiment()` functions
- Similar API patterns and naming conventions
- Automatic project/experiment creation helpers

### 2. Evaluation Trace Structure (IMPORTANT)
Each evaluation case must be its own root trace with nested child spans:
- **Root span**: Named "eval" with `type: "eval"` attribute
- **Child spans**: 
  - "task" span (with `type: "task"`) for executing the evaluation function
  - "score" span (with `type: "score"`) for calculating scores
- **Attributes on eval span**:
  - `input`: The actual input value (e.g., the question for MathProblem)
  - `output`: The actual output value (e.g., the result for Answer)
  - `expected`: The expected value if available
  - `score.<name>`: Score values (e.g., `score.accuracy`, `score.speed`)
- This nested structure matches how the Go SDK and JavaScript SDK work

### 3. OpenAI SDK
- Must use the official OpenAI Java SDK: https://github.com/openai/openai-java
- NOT the community versions (e.g., TheoKanning)

### 4. Model Selection
- Default to using GPT-4o in examples (not GPT-3.5-turbo)

## Common Issues and Solutions

### HTTP 403 Errors
1. First check if the endpoint path exists (403 can mean 404 due to API quirk)
2. Verify API key is set correctly
3. Check if hitting staging vs production endpoints

### HTTP 400 Errors
- Usually means the request format is correct but data is invalid
- Check OTLP data format matches expectations
- Verify all required fields are present

### Jackson Deserialization Errors
- The API may return additional fields not in our model classes
- Fixed by setting `FAIL_ON_UNKNOWN_PROPERTIES` to false in ObjectMapper
- Project response includes: id, name, org_id, created_at, updated_at, user_id, settings, etc.
- Experiment response includes: id, project_id, name, description, created_at, updated_at, etc.

## Testing Commands

```bash
# Run with debugging
export BRAINTRUST_API_KEY='your-key'
export BRAINTRUST_API_URL='https://staging-api.braintrust.dev'  # for testing
./gradlew :examples:runGoStyleExperiment

# Debug authorization issues
./gradlew :examples:runDebugAuth
```

## File Structure
- Java SDK core: `/src/main/java/dev/braintrust/`
- Examples: `/examples/src/main/java/dev/braintrust/examples/`
- Scala module: `/braintrust-scala/`

## Build and Format
```bash
# Format code
./gradlew spotlessApply

# Run tests
./gradlew test

# Build everything
./gradlew build
```