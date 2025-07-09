package dev.braintrust.eval;

/**
 * Exception thrown during evaluation execution.
 */
public class EvaluationException extends RuntimeException {
    
    public EvaluationException(String message) {
        super(message);
    }
    
    public EvaluationException(String message, Throwable cause) {
        super(message, cause);
    }
}