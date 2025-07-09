package dev.braintrust.scala

import dev.braintrust.log.{BraintrustLogger => JavaLogger}

/**
 * Scala-friendly logging API.
 */
object BraintrustLogger {
  
  def info(message: String, args: Any*): Unit = 
    JavaLogger.info(message, args.map(_.asInstanceOf[Object]): _*)
  
  def debug(message: String, args: Any*): Unit = 
    JavaLogger.debug(message, args.map(_.asInstanceOf[Object]): _*)
  
  def warn(message: String, args: Any*): Unit = 
    JavaLogger.warn(message, args.map(_.asInstanceOf[Object]): _*)
  
  def error(message: String, args: Any*): Unit = 
    JavaLogger.error(message, args.map(_.asInstanceOf[Object]): _*)
  
  def error(message: String, throwable: Throwable, args: Any*): Unit = 
    JavaLogger.error(message, throwable, args.map(_.asInstanceOf[Object]): _*)
  
  def setDebugEnabled(enabled: Boolean): Unit = 
    JavaLogger.setDebugEnabled(enabled)
  
  def isDebugEnabled: Boolean = 
    JavaLogger.isDebugEnabled
}