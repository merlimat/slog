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
 * A builder for constructing a child logger with multiple context attributes
 * batched into a single chain node, avoiding intermediate Logger instances.
 */
public interface LoggerBuilder {

    /**
     * Inherits all context attributes from another logger. Can be called
     * multiple times to compose context from several sources — attrs are
     * appended in call order.
     *
     * <pre>{@code
     * Logger log = Logger.get(MyClass.class).with()
     *     .ctx(producerLog)   // adds topic, clientAddr
     *     .ctx(requestLog)    // adds requestId, traceId
     *     .attr("extra", v)
     *     .build();
     * // order: producerLog attrs → requestLog attrs → extra
     * }</pre>
     *
     * @param other the logger whose context to inherit
     * @return this builder, for chaining
     */
    LoggerBuilder ctx(Logger other);

    /**
     * Adds a context attribute to the child logger being built.
     *
     * @param key   the attribute name
     * @param value the attribute value
     * @return this builder, for chaining
     */
    LoggerBuilder attr(String key, Object value);

    /**
     * Adds a lazily-evaluated context attribute. The supplier is invoked each
     * time a log event carrying this attribute is emitted, making it suitable
     * for values that change over time (e.g., connection state, queue depth).
     *
     * @param key   the attribute name
     * @param value a supplier that produces the attribute value at emit time
     * @return this builder, for chaining
     */
    LoggerBuilder attr(String key, Supplier<?> value);

    /**
     * Builds and returns the child logger with all accumulated attributes
     * as a single chain node.
     *
     * @return a new {@code Logger} with the added context attributes
     */
    Logger build();
}
