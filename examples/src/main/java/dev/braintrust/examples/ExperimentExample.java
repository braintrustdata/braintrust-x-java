package dev.braintrust.examples;

import dev.braintrust.config.BraintrustConfig;
import dev.braintrust.eval.Eval;

public class ExperimentExample {
    public static void main(String[] args) throws Exception {
        var config = BraintrustConfig.fromEnvironment();

        var eval = Eval.<String, String, String>builder()
                .name("java-eval-x")
                .config(config)
                .cases(Eval.Case.of("foo", "foo-type"),
                        Eval.Case.of("bar", "bar-type"))
                .task(inputStr -> inputStr + "-type")
                .scorer(Eval.Scorer.of("always_happy", result -> 1.0))
                .build();
        var result = eval.run();
        System.out.println(result.createReportString());
    }
}