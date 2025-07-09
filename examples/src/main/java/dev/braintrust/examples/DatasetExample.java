package dev.braintrust.examples;

import dev.braintrust.api.BraintrustApiClient;
import dev.braintrust.config.BraintrustConfig;
import dev.braintrust.eval.Evaluation;
import dev.braintrust.eval.Scorer;
import dev.braintrust.trace.BraintrustContext;
import dev.braintrust.trace.BraintrustTracing;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Example showing dataset management and API usage with modern Java patterns.
 * Demonstrates CompletableFuture composition, Optional handling, and functional transformations.
 */
public class DatasetExample {
    
    // Domain models using records
    record QAExample(String question, String context, String answer) {}
    record ModelOutput(String answer, double confidence) {}
    
    public static void main(String[] args) throws Exception {
        // Initialize configuration
        var config = BraintrustConfig.fromEnvironment();
        var openTelemetry = BraintrustTracing.quickstart(config);
        var apiClient = new BraintrustApiClient(config);
        
        // Create or get project
        var projectFuture = apiClient.createProject("qa-evaluation-demo")
            .exceptionally(error -> {
                System.err.println("Failed to create project: " + error);
                throw new RuntimeException(error);
            });
        
        var project = projectFuture.get();
        System.out.println("Using project: " + project.name() + " (ID: " + project.id() + ")");
        
        // Set project context for all spans
        var context = BraintrustContext.forProject(project.id())
            .storeInContext(Context.current());
        
        try (var scope = context.makeCurrent()) {
            // Create dataset
            var datasetFuture = createOrUpdateDataset(apiClient, project.id());
            var dataset = datasetFuture.get();
            
            // Load evaluation data
            var testData = loadTestData();
            
            // Run evaluation with experiment tracking
            var experimentFuture = apiClient.createExperiment(
                new BraintrustApiClient.CreateExperimentRequest(
                    project.id(),
                    "qa-model-v2-eval",
                    Optional.of("Evaluating QA model v2 with confidence scores"),
                    Optional.empty()
                )
            );
            
            var experiment = experimentFuture.get();
            
            // Run evaluation with experiment context
            var results = Evaluation.<QAExample, ModelOutput>builder()
                .name("QA Model Evaluation")
                .data(testData)
                .task(DatasetExample::runQAModel)
                .scorer(createAnswerScorer())
                .scorer(createConfidenceScorer())
                .scorer(createLengthScorer())
                .experimentId(experiment.id())
                .parallel(true)
                .run();
            
            // Analyze results
            analyzeResults(results);
            
            // Upload results to dataset for future use
            uploadResultsToDataset(apiClient, dataset.id(), results).get();
            
            System.out.println("\nEvaluation completed successfully!");
        }
        
        // Clean up
        apiClient.close();
        Thread.sleep(2000); // Allow spans to export
    }
    
    private static CompletableFuture<BraintrustApiClient.Dataset> createOrUpdateDataset(
            BraintrustApiClient client, String projectId) {
        
        return client.createDataset(new BraintrustApiClient.CreateDatasetRequest(
            projectId,
            "qa-test-dataset",
            Optional.of("Question-answering test cases")
        )).thenCompose(dataset -> {
            // Add sample events to dataset
            var events = List.of(
                new BraintrustApiClient.DatasetEvent(
                    Map.of("question", "What is the capital of France?",
                           "context", "France is a country in Europe."),
                    "Paris"
                ),
                new BraintrustApiClient.DatasetEvent(
                    Map.of("question", "Who wrote Romeo and Juliet?",
                           "context", "Shakespeare was an English playwright."),
                    "William Shakespeare"
                )
            );
            
            return client.insertDatasetEvents(dataset.id(), events)
                .thenApply(response -> {
                    System.out.println("Inserted " + response.insertedCount() + " events");
                    return dataset;
                });
        });
    }
    
    private static List<QAExample> loadTestData() {
        return List.of(
            new QAExample(
                "What is the capital of France?",
                "France is a country in Western Europe. Its capital city is known for the Eiffel Tower.",
                "Paris"
            ),
            new QAExample(
                "Who wrote Romeo and Juliet?",
                "William Shakespeare was an English playwright and poet of the Renaissance period.",
                "William Shakespeare"
            ),
            new QAExample(
                "What is the speed of light?",
                "Light travels at approximately 299,792,458 meters per second in a vacuum.",
                "299,792,458 meters per second"
            ),
            new QAExample(
                "What is machine learning?",
                "Machine learning is a subset of artificial intelligence that enables systems to learn from data.",
                "A subset of AI that enables systems to learn from data"
            )
        );
    }
    
    private static ModelOutput runQAModel(QAExample example) {
        var span = Span.current();
        span.setAttribute("qa.question_length", example.question.length());
        span.setAttribute("qa.context_length", example.context.length());
        
        // Simulate model inference
        try {
            Thread.sleep(50 + (long)(Math.random() * 100));
            
            // Simple rule-based "model" for demo
            var confidence = 0.5 + Math.random() * 0.5;
            var answer = generateAnswer(example);
            
            span.setAttribute("qa.answer_length", answer.length());
            span.setAttribute("qa.confidence", confidence);
            
            return new ModelOutput(answer, confidence);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
    
    private static String generateAnswer(QAExample example) {
        // Simulate different quality answers
        var random = Math.random();
        if (random > 0.7) {
            return example.answer; // Correct answer
        } else if (random > 0.3) {
            return example.answer.substring(0, Math.min(10, example.answer.length())) + "..."; // Partial
        } else {
            return "I don't know"; // Wrong answer
        }
    }
    
    private static Scorer<QAExample, ModelOutput> createAnswerScorer() {
        return Scorer.of("answer_accuracy", (example, output) -> {
            if (output.answer.equals(example.answer)) {
                return 1.0;
            } else if (example.answer.toLowerCase().contains(output.answer.toLowerCase()) ||
                       output.answer.toLowerCase().contains(example.answer.toLowerCase())) {
                return 0.5;
            }
            return 0.0;
        });
    }
    
    private static Scorer<QAExample, ModelOutput> createConfidenceScorer() {
        return Scorer.of("confidence_calibration", (example, output) -> {
            var isCorrect = output.answer.equals(example.answer);
            var confidence = output.confidence;
            
            // Good calibration: high confidence when correct, low when incorrect
            if (isCorrect && confidence > 0.7) {
                return 1.0;
            } else if (!isCorrect && confidence < 0.3) {
                return 1.0;
            } else {
                return 1.0 - Math.abs(confidence - (isCorrect ? 1.0 : 0.0));
            }
        });
    }
    
    private static Scorer<QAExample, ModelOutput> createLengthScorer() {
        return Scorer.of("answer_completeness", (example, output) -> {
            var expectedLength = example.answer.length();
            var actualLength = output.answer.length();
            
            if (actualLength == 0) return 0.0;
            
            var ratio = (double) actualLength / expectedLength;
            if (ratio > 2.0) return 0.5; // Too verbose
            if (ratio < 0.5) return ratio; // Too short
            return 1.0;
        });
    }
    
    private static void analyzeResults(dev.braintrust.eval.EvaluationResults<QAExample, ModelOutput> results) {
        System.out.println("\n=== Evaluation Analysis ===");
        
        // Overall metrics
        System.out.println(results.generateReport());
        
        // Confidence analysis
        var avgConfidence = results.successful()
            .mapToDouble(r -> r.output().confidence)
            .average()
            .orElse(0.0);
        
        System.out.printf("\nAverage model confidence: %.3f%n", avgConfidence);
        
        // Find overconfident wrong answers
        System.out.println("\nOverconfident mistakes (confidence > 0.7, score < 0.5):");
        results.successful()
            .filter(r -> r.output().confidence > 0.7 && r.averageScore() < 0.5)
            .forEach(r -> System.out.printf("  Q: %s%n  A: %s (confidence: %.2f)%n",
                r.input().question, r.output().answer, r.output().confidence));
        
        // Score correlation analysis
        var scoresByQuestion = results.successful()
            .collect(java.util.stream.Collectors.groupingBy(
                r -> r.input().question,
                java.util.stream.Collectors.averagingDouble(
                    dev.braintrust.eval.EvaluationResult::averageScore
                )
            ));
        
        System.out.println("\nHardest questions:");
        scoresByQuestion.entrySet().stream()
            .sorted(Map.Entry.comparingByValue())
            .limit(2)
            .forEach(entry -> System.out.printf("  %s (avg score: %.2f)%n",
                entry.getKey(), entry.getValue()));
    }
    
    private static CompletableFuture<Void> uploadResultsToDataset(
            BraintrustApiClient client,
            String datasetId,
            dev.braintrust.eval.EvaluationResults<QAExample, ModelOutput> results) {
        
        // Convert results to dataset events
        var events = results.successful()
            .map(r -> new BraintrustApiClient.DatasetEvent(
                Map.of(
                    "question", r.input().question,
                    "context", r.input().context,
                    "expected", r.input().answer
                ),
                Optional.of(Map.of(
                    "answer", r.output().answer,
                    "confidence", r.output().confidence,
                    "scores", r.scores()
                )),
                Optional.of(Map.of(
                    "evaluation_run", results.name(),
                    "duration_ms", r.duration().toMillis()
                ))
            ))
            .toList();
        
        return client.insertDatasetEvents(datasetId, events)
            .thenAccept(response -> 
                System.out.println("\nUploaded " + response.insertedCount() + 
                                 " evaluation results to dataset"));
    }
}