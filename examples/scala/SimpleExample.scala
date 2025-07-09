package dev.braintrust.examples.scala

import dev.braintrust.scala._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Success, Failure}

object SimpleExample extends App {
  
  // Initialize Braintrust
  val otel = BraintrustTracing.quickstart { config =>
    config
      .withServiceName("scala-example")
      .withDebug(true)
  }
  
  implicit val tracer = BraintrustTracing.getTracer(otel)
  
  // Example 1: Simple synchronous span
  println("=== Example 1: Synchronous Span ===")
  tracer.span("process-data") { span =>
    span.setAttribute("data.size", 100)
    
    // Simulate some work
    Thread.sleep(100)
    
    span.addScore("accuracy", 0.95)
    span.addUsage(150, 50, 0.003)
    
    println("Processed data successfully")
  }
  
  // Example 2: Async operation
  println("\n=== Example 2: Async Operation ===")
  val futureResult = tracer.spanAsync("async-api-call") { span =>
    Future {
      span.setAttribute("api.endpoint", "/v1/generate")
      
      // Simulate API call
      Thread.sleep(200)
      
      if (math.random() > 0.1) {
        span.addScore("success", 1.0)
        "API call successful"
      } else {
        throw new Exception("Random API failure")
      }
    }
  }
  
  futureResult.onComplete {
    case Success(result) =>
      println(s"Async result: $result")
    case Failure(error) =>
      println(s"Async error: ${error.getMessage}")
  }
  
  // Example 3: Nested spans
  println("\n=== Example 3: Nested Spans ===")
  tracer.span("parent-operation") { parentSpan =>
    parentSpan.setAttribute("operation.type", "batch")
    
    val results = (1 to 3).map { i =>
      tracer.span(s"child-operation-$i") { childSpan =>
        childSpan.setAttribute("item.id", i)
        Thread.sleep(50)
        i * 2
      }
    }
    
    parentSpan.setAttribute("results.sum", results.sum)
    println(s"Batch results: ${results.mkString(", ")}")
  }
  
  // Example 4: Error handling
  println("\n=== Example 4: Error Handling ===")
  val errorResult = tracer.spanAsync("error-prone-operation") { span =>
    Future {
      span.setAttribute("retry.attempt", 1)
      
      try {
        if (math.random() < 0.5) {
          throw new RuntimeException("Simulated error")
        }
        "Success"
      } catch {
        case e: Exception =>
          span.recordException(e)
          span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR)
          throw e
      }
    }
  }.recover {
    case e: Exception =>
      println(s"Handled error: ${e.getMessage}")
      "Recovered from error"
  }
  
  // Wait for async operations
  Thread.sleep(1000)
  
  println("\n=== Examples Complete ===")
  println("Check your Braintrust dashboard for traces!")
}