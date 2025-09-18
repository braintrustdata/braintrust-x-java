package dev.braintrust.eval;

/**
 * A task executes amn EvalCase and returns a result
 */
public interface Task<INPUT, EXPECTED, RESULT> {
    RESULT apply(EvalCase<INPUT, EXPECTED> evalCase);
}
