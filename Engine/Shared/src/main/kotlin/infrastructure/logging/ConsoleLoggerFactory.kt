package org.genhttp.engine.shared.infrastructure.logging

import org.slf4j.ILoggerFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.Marker
import org.slf4j.event.Level
import org.slf4j.helpers.LegacyAbstractLogger
import org.slf4j.helpers.MessageFormatter
import org.slf4j.helpers.NOPLoggerFactory

import java.util.concurrent.ConcurrentHashMap

/**
 * Logs to the console, used by default if no SLF4J provider is available
 * (GenHTTP logs to the console by default as well).
 */
public class ConsoleLoggerFactory(private val minimumLevel: Level = Level.INFO) : ILoggerFactory {

    private val loggers = ConcurrentHashMap<String, Logger>()

    override fun getLogger(name: String): Logger = loggers.computeIfAbsent(name) { ConsoleLogger(it, minimumLevel) }

    /**
     * Creates the default logger factory.
     */
    public companion object {

        /**
         * Returns the logger factory of the SLF4J provider on the classpath, or a console logger if there is none.
         */
        public fun createDefault(): ILoggerFactory {
            val factory = LoggerFactory.getILoggerFactory()
            return if (factory is NOPLoggerFactory) ConsoleLoggerFactory() else factory
        }

    }

    private class ConsoleLogger(private val loggerName: String, private val minimumLevel: Level) : LegacyAbstractLogger() {

        init {
            name = loggerName
        }

        override fun isTraceEnabled(): Boolean = enabled(Level.TRACE)

        override fun isDebugEnabled(): Boolean = enabled(Level.DEBUG)

        override fun isInfoEnabled(): Boolean = enabled(Level.INFO)

        override fun isWarnEnabled(): Boolean = enabled(Level.WARN)

        override fun isErrorEnabled(): Boolean = enabled(Level.ERROR)

        private fun enabled(level: Level): Boolean = level.toInt() >= minimumLevel.toInt()

        override fun getFullyQualifiedCallerName(): String? = null

        override fun handleNormalizedLoggingCall(level: Level, marker: Marker?, messagePattern: String?, arguments: Array<out Any?>?, throwable: Throwable?) {
            val message = MessageFormatter.basicArrayFormat(messagePattern, arguments)

            val target = if (level.toInt() >= Level.WARN.toInt()) System.err else System.out

            synchronized(target) {
                target.println("${level.name.lowercase()}: $loggerName")
                target.println("      $message")
                throwable?.printStackTrace(target)
            }
        }

    }

}
