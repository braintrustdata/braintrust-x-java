package dev.braintrust.scala

import dev.braintrust.eval.{Evaluation => JavaEvaluation, EvaluationConfig}
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._

/**
 * Scala-friendly evaluation API.
 */
object Evaluation {
  
  /**
   * Run an evaluation with Scala futures.
   * 
   * @example {{{
   * case class TestCase(input: String, expected: String)
   * 
   * Evaluation.runAsync[TestCase, String]("My Eval") { test =>
   *   Future {
   *     // Your async evaluation logic
   *     callLLM(test.input)
   *   }
   * } { case (test, output) =>
   *   if (output.contains(test.expected)) 1.0 else 0.0
   * }
   * }}}
   */
  def runAsync[Input, Output](
    name: String,
    testCases: Seq[Input]
  )(
    evaluator: Input => Future[Output]
  )(
    scorer: (Input, Output) => Double
  )(implicit ec: ExecutionContext): Future[Unit] = {
    
    val config = EvaluationConfig.builder()
      .name(name)
      .build()
    
    val javaEval = JavaEvaluation.create[Input, Output](
      config,
      testCases.asJava,
      input => evaluator(input).asJava.toCompletableFuture,
      (input, output) => scorer(input, output)
    )
    
    Future {
      javaEval.run()
    }
  }
  
  /**
   * Simple synchronous evaluation.
   */
  def run[Input, Output](
    name: String,
    testCases: Seq[Input]
  )(
    evaluator: Input => Output
  )(
    scorer: (Input, Output) => Double
  ): Unit = {
    
    val config = EvaluationConfig.builder()
      .name(name)
      .build()
    
    JavaEvaluation.create[Input, Output](
      config,
      testCases.asJava,
      evaluator(_),
      (input, output) => scorer(input, output)
    ).run()
  }
}