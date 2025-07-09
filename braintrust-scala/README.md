# Braintrust Scala SDK

Scala-friendly wrappers around the Braintrust Java SDK, providing idiomatic Scala APIs while leveraging the robust Java implementation.

## Installation

Add to your `build.sbt`:

```scala
libraryDependencies += "dev.braintrust" %% "braintrust-scala" % "0.1.0"
```

## Quick Start

```scala
import dev.braintrust.scala._
import scala.concurrent.ExecutionContext.Implicits.global

// Initialize with builder DSL
val otel = BraintrustTracing.quickstart { config =>
  config
    .withApiKey("your-api-key")
    .withProjectId("my-project")
    .withServiceName("my-scala-service")
}

// Get a tracer
implicit val tracer = BraintrustTracing.getTracer(otel)

// Use spans with automatic resource management
tracer.span("my-operation") { span =>
  // Your code here
  span.addScore("quality", 0.95)
  span.addUsage(100, 50, 0.002)
}

// Async operations
val result = tracer.spanAsync("async-operation") { span =>
  Future {
    // Async work
    span.setAttribute("custom.attribute", "value")
    "result"
  }
}
```

## Evaluation Framework

```scala
import dev.braintrust.scala.Evaluation

case class QATest(question: String, expectedAnswer: String)

val tests = Seq(
  QATest("What is 2+2?", "4"),
  QATest("What is the capital of France?", "Paris")
)

// Run async evaluation
Evaluation.runAsync[QATest, String]("Q&A Evaluation")(tests) { test =>
  Future {
    // Call your LLM here
    callLLM(test.question)
  }
} { (test, output) =>
  // Score based on whether output contains expected answer
  if (output.toLowerCase.contains(test.expectedAnswer.toLowerCase)) 1.0 else 0.0
}
```

## Logging

```scala
import dev.braintrust.scala.BraintrustLogger

BraintrustLogger.info("Starting application version={}", "1.0.0")
BraintrustLogger.debug("Processing request id={}", requestId)
BraintrustLogger.error("Failed to process", exception, "requestId={}", requestId)
```

## Features

- **Idiomatic Scala API**: Uses Scala idioms like case classes, pattern matching, and futures
- **Automatic Resource Management**: Spans are automatically closed with `use` and `useAsync`
- **Type Safety**: Leverages Scala's type system for compile-time safety
- **Seamless Java Interop**: Can mix Scala and Java APIs as needed
- **Future Support**: Native support for Scala futures and async operations

## Examples

See the [examples directory](../examples/scala) for complete examples.