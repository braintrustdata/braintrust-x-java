# Braintrust Java SDK Fixes Summary

## Issues Fixed

### 1. API Endpoint Paths (Critical)
**Problem**: Using plural endpoints like `/projects` and `/experiments`
**Solution**: Changed to singular with `/v1/` prefix:
- `/projects` → `/v1/project`
- `/experiments` → `/v1/experiment`  
- `/datasets` → `/v1/dataset`

### 2. Missing x-bt-parent Header (Critical)
**Problem**: Traces were not appearing in experiments because the `x-bt-parent` header was missing
**Solution**: Created custom `BraintrustSpanExporter` that dynamically adds the header based on span attributes:
- Format: `experiment_id:<id>` or `project_id:<id>`
- This header tells Braintrust which experiment/project the traces belong to

### 3. Jackson Deserialization Errors
**Problem**: API responses included fields like `org_id` that weren't in our model classes
**Solution**: 
- Added `orgId` field to Project record
- Configured ObjectMapper to ignore unknown fields with `FAIL_ON_UNKNOWN_PROPERTIES = false`

### 4. Compilation Error in Example
**Problem**: `OpenTelemetry` interface doesn't have `getSdkTracerProvider()` method
**Solution**: Added instanceof check and cast to `OpenTelemetrySdk` when flushing spans

### 5. Missing Span Data
**Problem**: Evaluation spans were not logging input/output data
**Solution**: Added `input` and `output` attributes to evaluation spans

## Files Modified

1. `/src/main/java/dev/braintrust/api/BraintrustApiClient.java`
   - Updated all API endpoints to use `/v1/` prefix with singular names
   - Added `orgId` field to Project record
   - Configured Jackson to ignore unknown fields

2. `/src/main/java/dev/braintrust/trace/BraintrustSpanExporter.java` (NEW)
   - Custom exporter that dynamically sets `x-bt-parent` header
   - Groups spans by parent and exports with appropriate headers

3. `/src/main/java/dev/braintrust/trace/BraintrustTracing.java`
   - Changed to use custom `BraintrustSpanExporter` instead of standard OTLP exporter

4. `/src/main/java/dev/braintrust/eval/Evaluation.java`
   - Added input/output attributes to evaluation spans

5. `/examples/src/main/java/dev/braintrust/examples/SimpleExperimentWithRegistration.java`
   - Fixed SDK type check for flushing spans
   - Added proper shutdown sequence

6. `/CLAUDE.md` (NEW)
   - Documented all Braintrust API quirks and solutions
   - Critical reference for future development

## Key Learnings

1. **404 as 403**: Braintrust returns 403 "Invalid key=value pair" errors when hitting non-existent endpoints
2. **x-bt-parent Required**: This header is critical for traces to appear in experiments
3. **Singular API Paths**: Unlike typical REST APIs, Braintrust uses singular resource names
4. **Dynamic Headers**: Standard OTLP exporters don't support dynamic headers, requiring custom implementation

## Testing

The SDK now compiles successfully. With a valid API key, the Go-style experiment example should:
1. Create a project and experiment via the REST API
2. Run evaluations that generate traced spans
3. Export spans with proper `x-bt-parent` header
4. Display results in the Braintrust dashboard under the correct experiment