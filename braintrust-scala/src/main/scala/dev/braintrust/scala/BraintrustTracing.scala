package dev.braintrust.scala

import config.dev.braintrust.claude.BraintrustConfig
import dev.braintrust.trace.{BraintrustTracing => JavaTracing}
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.{Span, Tracer}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Try, Success, Failure}
import scala.jdk.DurationConverters._

/**
 * Scala-friendly API for Braintrust tracing.
 * Provides idiomatic Scala wrappers around the Java SDK.
 */
object BraintrustTracing {
  
  /**
   * Quick start with environment variables.
   */
  def quickstart(): OpenTelemetry = 
    JavaTracing.quickstart()
  
  /**
   * Quick start with configuration builder DSL.
   * 
   * @example {{{
   * val otel = BraintrustTracing.quickstart { config =>
   *   config
   *     .withApiKey("your-key")
   *     .withProjectId("my-project")
   *     .withServiceName("my-service")
   * }
   * }}}
   */
  def quickstart(configure: ConfigBuilder => ConfigBuilder): OpenTelemetry = {
    val builder = new ConfigBuilder()
    val configured = configure(builder)
    JavaTracing.quickstart(configured.build(), configured.customize)
  }
  
  /**
   * Get a tracer with Braintrust instrumentation.
   */
  def getTracer(implicit otel: OpenTelemetry): Tracer = 
    JavaTracing.getTracer(otel)
  
  /**
   * Configuration builder with fluent Scala API.
   */
  class ConfigBuilder {
    private var config = BraintrustConfig.builder()
    private var tracingCustomizer: JavaTracing.Builder => Unit = _ => ()
    
    def withApiKey(key: String): ConfigBuilder = {
      config.apiKey(key)
      this
    }
    
    def withApiUrl(url: String): ConfigBuilder = {
      config.apiUrl(url)
      this
    }
    
    def withProjectId(id: String): ConfigBuilder = {
      config.defaultProjectId(id)
      this
    }
    
    def withDebug(enabled: Boolean = true): ConfigBuilder = {
      config.debug(enabled)
      this
    }
    
    def withServiceName(name: String): ConfigBuilder = {
      tracingCustomizer = { b => b.serviceName(name) }
      this
    }
    
    def withResourceAttribute(key: String, value: String): ConfigBuilder = {
      val prev = tracingCustomizer
      tracingCustomizer = { b => 
        prev(b)
        b.resourceAttribute(key, value)
      }
      this
    }
    
    def withExportInterval(duration: scala.concurrent.duration.Duration): ConfigBuilder = {
      val prev = tracingCustomizer
      tracingCustomizer = { b =>
        prev(b)
        b.exportInterval(duration.toJava)
      }
      this
    }
    
    private[scala] def build(): BraintrustConfig = config.build()
    private[scala] def customize: java.util.function.Consumer[JavaTracing.Builder] = 
      new java.util.function.Consumer[JavaTracing.Builder] {
        def accept(b: JavaTracing.Builder): Unit = tracingCustomizer(b)
      }
  }
}

/**
 * Implicit conversions and syntax extensions.
 */
package object scala {
  
  /**
   * Adds Scala-friendly methods to spans.
   */
  implicit class RichSpan(val span: Span) extends AnyVal {
    
    /**
     * Execute a block within this span's scope.
     */
    def use[T](block: => T): T = {
      val scope = span.makeCurrent()
      try {
        block
      } finally {
        scope.close()
        span.end()
      }
    }
    
    /**
     * Execute an async block within this span's scope.
     */
    def useAsync[T](block: => Future[T])(implicit ec: ExecutionContext): Future[T] = {
      val scope = span.makeCurrent()
      block.andThen { case _ =>
        scope.close()
        span.end()
      }
    }
    
    /**
     * Record a Try result and end the span.
     */
    def recordResult[T](result: Try[T]): Try[T] = {
      result match {
        case Success(_) =>
          span.setStatus(io.opentelemetry.api.trace.StatusCode.OK)
        case Failure(e) =>
          span.recordException(e)
          span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, e.getMessage)
      }
      span.end()
      result
    }
    
    /**
     * Add a score to this span.
     */
    def addScore(name: String, value: Double): Span = {
      JavaTracing.SpanUtils.addScore(name, value)
      span
    }
    
    /**
     * Add usage metrics to this span.
     */
    def addUsage(promptTokens: Long, completionTokens: Long, cost: Double): Span = {
      JavaTracing.SpanUtils.addUsageMetrics(promptTokens, completionTokens, cost)
      span
    }
  }
  
  /**
   * Adds Scala-friendly methods to tracers.
   */
  implicit class RichTracer(val tracer: Tracer) extends AnyVal {
    
    /**
     * Create and use a span in one call.
     * 
     * @example {{{
     * tracer.span("operation") { span =>
     *   // do work
     *   span.addScore("quality", 0.95)
     * }
     * }}}
     */
    def span[T](name: String)(block: Span => T): T = {
      val span = tracer.spanBuilder(name).startSpan()
      span.use(block(span))
    }
    
    /**
     * Create and use a span with async operation.
     */
    def spanAsync[T](name: String)(block: Span => Future[T])(implicit ec: ExecutionContext): Future[T] = {
      val span = tracer.spanBuilder(name).startSpan()
      span.useAsync(block(span))
    }
  }
}
