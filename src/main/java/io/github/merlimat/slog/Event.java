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

import java.util.function.Supplier;

/**
 * A fluent builder for constructing a structured log event.
 *
 * <p>Obtained from {@link Logger#info()}, {@link Logger#debug()}, etc. When the
 * corresponding level is disabled, a no-op singleton is returned so that all chained
 * calls are effectively free.
 *
 * <p>Example usage:
 * <pre>{@code
 * logger.info()
 *       .attr("orderId", orderId)
 *       .attr("items", itemCount)
 *       .timed()
 *       .log("Order processed");
 * }</pre>
 */
public interface Event {

    /**
     * Adds a structured key-value attribute to this event.
     *
     * @param key   the attribute name
     * @param value the attribute value
     * @return this event, for chaining
     */
    Event attr(String key, Object value);

    /**
     * Adds a {@code long} attribute, avoiding autoboxing when the level is disabled.
     *
     * @param key   the attribute name
     * @param value the attribute value
     * @return this event, for chaining
     */
    Event attr(String key, long value);

    /**
     * Adds an {@code int} attribute, avoiding autoboxing when the level is disabled.
     *
     * @param key   the attribute name
     * @param value the attribute value
     * @return this event, for chaining
     */
    Event attr(String key, int value);

    /**
     * Adds a {@code double} attribute, avoiding autoboxing when the level is disabled.
     *
     * @param key   the attribute name
     * @param value the attribute value
     * @return this event, for chaining
     */
    Event attr(String key, double value);

    /**
     * Adds a {@code float} attribute, avoiding autoboxing when the level is disabled.
     *
     * @param key   the attribute name
     * @param value the attribute value
     * @return this event, for chaining
     */
    Event attr(String key, float value);

    /**
     * Adds a {@code boolean} attribute, avoiding autoboxing when the level is disabled.
     *
     * @param key   the attribute name
     * @param value the attribute value
     * @return this event, for chaining
     */
    Event attr(String key, boolean value);

    /**
     * Adds a lazily-evaluated attribute. The supplier is invoked only when the
     * event is actually emitted, making it suitable for values that are expensive
     * to compute.
     *
     * @param key   the attribute name
     * @param value a supplier that produces the attribute value at emit time
     * @return this event, for chaining
     */
    Event attr(String key, Supplier<?> value);

    /**
     * Attaches an exception to this event, including the full stack trace.
     * No-op if {@code t} is {@code null}.
     *
     * @param t the throwable to attach, may be {@code null}
     * @return this event, for chaining
     */
    Event exception(Throwable t);

    /**
     * Attaches only the exception's message as an {@code "exception"} attribute,
     * without the full stack trace. If the throwable's message is {@code null},
     * this is a no-op.
     *
     * @param t the throwable whose message to attach
     * @return this event, for chaining
     */
    Event exceptionMessage(Throwable t);

    /**
     * Starts a timer for this event. The elapsed duration between this call and
     * {@link #log(String)} will be recorded as a {@code durationMs} attribute.
     *
     * @return this event, for chaining
     */
    Event timed();

    /**
     * Emits the log event with the given message. This is the terminal operation
     * that sends the assembled record to the {@link Handler}.
     *
     * @param msg the log message
     */
    void log(String msg);

    /**
     * Emits the log event with a lazily-evaluated message. The supplier is invoked
     * only when the level is enabled, making it suitable for messages that are
     * expensive to construct.
     *
     * @param msgSupplier a supplier that produces the log message
     */
    void log(Supplier<String> msgSupplier);

    /**
     * Emits the log event with a formatted message using Java's printf-style
     * format specifiers ({@code %s}, {@code %d}, {@code %f}, etc.).
     * Formatting is deferred — it only happens when the level is enabled.
     *
     * <p><b>Note:</b> This uses {@link String#format} syntax, not SLF4J's
     * {@code {}} placeholders.
     *
     * <pre>{@code
     * log.info().attr("op", "resize")
     *     .logf("Processed %d items in %dms", count, elapsed);
     * }</pre>
     *
     * @param format the format string (as in {@link String#format})
     * @param args   the format arguments
     * @see java.util.Formatter
     */
    void logf(String format, Object... args);
}
