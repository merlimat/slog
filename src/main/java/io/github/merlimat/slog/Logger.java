/*
 * Copyright 2026 Matteo Merli <matteo.merli@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.merlimat.slog;

import io.github.merlimat.slog.impl.LoggerDiscovery;
import java.util.function.Consumer;

/**
 * A structured logger that emits log records with attached key-value attributes.
 *
 * <p>Loggers are immutable and safe to share across threads. Calling {@link #with}
 * returns a new Logger instance with additional context attributes — the original
 * is never modified. This makes it natural to derive component-scoped loggers:
 *
 * <pre>{@code
 * Logger slog = Logger.get(Producer.class).with()
 *     .attr("topic", topicName)
 *     .attr("clientAddr", remoteAddr)
 *     .build();
 *
 * // All subsequent logs from this logger include topic and clientAddr
 * slog.info().attr("msgId", id).attr("size", payload.length).log("Message published");
 * }</pre>
 *
 * <p><b>Zero overhead when disabled:</b> All logging methods check
 * the backend's level guard before constructing any objects. The fluent
 * {@code info()}, {@code error()}, etc. return a no-op {@link Event} singleton when the
 * level is disabled.
 *
 * <p><b>Duplicate keys:</b> When the same key appears at multiple levels
 * (parent context, inherited via {@link LoggerBuilder#ctx}, builder attrs, or
 * per-event kvs), all occurrences are preserved in order. Resolution
 * (last-writer-wins, etc.) is left to the logging backend.
 */
public interface Logger {

    /**
     * Creates a new structured logger named after the given class.
     *
     * <p>Example:
     * <pre>{@code
     * Logger log = Logger.get(MyService.class).with()
     *     .attr("topic", topic)
     *     .attr("clientAddr", addr)
     *     .build();
     * }</pre>
     *
     * @param clazz the class whose name will be used as the logger name
     * @return a new {@code Logger} instance
     */
    static Logger get(Class<?> clazz) {
        return get(clazz.getName());
    }

    /**
     * Creates a new structured logger with the given name.
     *
     * @param name the logger name (typically a fully-qualified class name)
     * @return a new {@code Logger} instance
     */
    static Logger get(String name) {
        return LoggerDiscovery.createLogger(name);
    }

    /**
     * Returns the logger name.
     *
     * @return the logger name
     */
    String name();

    /**
     * Returns a {@link LoggerBuilder} for constructing a child logger with multiple
     * context attributes in a single step.
     *
     * <pre>{@code
     * Logger child = parent.with()
     *     .attr("topic", topic)
     *     .attr("clientAddr", addr)
     *     .attr("namespace", ns)
     *     .build();
     * }</pre>
     *
     * @return a new builder rooted at this logger
     */
    default LoggerBuilder with() {
        return LoggerDiscovery.newBuilder(this);
    }

    // --- Logging methods ---

    /**
     * Logs a message at TRACE level. No-op if TRACE is disabled.
     *
     * @param msg the log message
     */
    void trace(String msg);

    /**
     * Logs a printf-formatted message at TRACE level. No-op if TRACE is disabled.
     *
     * @param format the format string (as in {@link String#format})
     * @param args   the format arguments
     */
    void tracef(String format, Object... args);

    /**
     * Returns a fluent TRACE-level event builder. No-op singleton if TRACE is disabled.
     *
     * @return an {@link Event} builder for TRACE level
     */
    Event trace();

    /**
     * Passes an event builder to the consumer at TRACE level. Skipped if disabled.
     *
     * @param consumer the consumer that builds and emits the event
     */
    void trace(Consumer<Event> consumer);

    /**
     * Logs a message at DEBUG level. No-op if DEBUG is disabled.
     *
     * @param msg the log message
     */
    void debug(String msg);

    /**
     * Logs a printf-formatted message at DEBUG level. No-op if DEBUG is disabled.
     *
     * @param format the format string (as in {@link String#format})
     * @param args   the format arguments
     */
    void debugf(String format, Object... args);

    /**
     * Returns a fluent DEBUG-level event builder. No-op singleton if DEBUG is disabled.
     *
     * @return an {@link Event} builder for DEBUG level
     */
    Event debug();

    /**
     * Passes an event builder to the consumer at DEBUG level. Skipped if disabled.
     *
     * @param consumer the consumer that builds and emits the event
     */
    void debug(Consumer<Event> consumer);

    /**
     * Logs a message at INFO level. No-op if INFO is disabled.
     *
     * @param msg the log message
     */
    void info(String msg);

    /**
     * Logs a printf-formatted message at INFO level. No-op if INFO is disabled.
     *
     * @param format the format string (as in {@link String#format})
     * @param args   the format arguments
     */
    void infof(String format, Object... args);

    /**
     * Returns a fluent INFO-level event builder. No-op singleton if INFO is disabled.
     *
     * @return an {@link Event} builder for INFO level
     */
    Event info();

    /**
     * Passes an event builder to the consumer at INFO level. Skipped if disabled.
     *
     * @param consumer the consumer that builds and emits the event
     */
    void info(Consumer<Event> consumer);

    /**
     * Logs a message at WARN level. No-op if WARN is disabled.
     *
     * @param msg the log message
     */
    void warn(String msg);

    /**
     * Logs a printf-formatted message at WARN level. No-op if WARN is disabled.
     *
     * @param format the format string (as in {@link String#format})
     * @param args   the format arguments
     */
    void warnf(String format, Object... args);

    /**
     * Returns a fluent WARN-level event builder. No-op singleton if WARN is disabled.
     *
     * @return an {@link Event} builder for WARN level
     */
    Event warn();

    /**
     * Passes an event builder to the consumer at WARN level. Skipped if disabled.
     *
     * @param consumer the consumer that builds and emits the event
     */
    void warn(Consumer<Event> consumer);

    /**
     * Logs a message at ERROR level. No-op if ERROR is disabled.
     *
     * @param msg the log message
     */
    void error(String msg);

    /**
     * Logs a printf-formatted message at ERROR level. No-op if ERROR is disabled.
     *
     * @param format the format string (as in {@link String#format})
     * @param args   the format arguments
     */
    void errorf(String format, Object... args);

    /**
     * Returns a fluent ERROR-level event builder. No-op singleton if ERROR is disabled.
     *
     * @return an {@link Event} builder for ERROR level
     */
    Event error();

    /**
     * Passes an event builder to the consumer at ERROR level. Skipped if disabled.
     *
     * @param consumer the consumer that builds and emits the event
     */
    void error(Consumer<Event> consumer);

}
