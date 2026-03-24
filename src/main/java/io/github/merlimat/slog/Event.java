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

/**
 * A fluent builder for constructing a structured log event.
 *
 * <p>Obtained from {@link Logger#atInfo()}, {@link Logger#atDebug()}, etc. When the
 * corresponding level is disabled, a no-op singleton is returned so that all chained
 * calls are effectively free.
 *
 * <p>Example usage:
 * <pre>{@code
 * logger.atInfo()
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
     * Attaches an exception to this event.
     *
     * @param t the throwable to attach
     * @return this event, for chaining
     */
    Event exception(Throwable t);

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
}
