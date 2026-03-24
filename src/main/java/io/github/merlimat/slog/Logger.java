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

import io.github.merlimat.slog.handler.HandlerDiscovery;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

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
 * slog.info("Message published", "msgId", id, "size", payload.length);
 * }</pre>
 *
 * <p><b>Zero overhead when disabled:</b> All logging methods check
 * {@link Handler#isEnabled} before constructing any objects. The fluent
 * {@code info()}, {@code error()}, etc. return a no-op {@link Event} singleton when the
 * level is disabled.
 *
 * <p><b>Duplicate keys:</b> When the same key appears at multiple levels
 * (parent context, inherited via {@link Builder#ctx}, builder attrs, or
 * per-event kvs), all occurrences are preserved in order. Resolution
 * (last-writer-wins, etc.) is left to the logging backend.
 */
public class Logger {
    private final String name;
    private final Handler handler;
    private final AttrChain contextAttrs;
    private final Clock clock;

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
    public static Logger get(Class<?> clazz) {
        return get(clazz.getName());
    }

    /**
     * Creates a new structured logger with the given name.
     *
     * @param name the logger name (typically a fully-qualified class name)
     * @return a new {@code Logger} instance
     */
    public static Logger get(String name) {
        return new Logger(name, HandlerDiscovery.get(), AttrChain.EMPTY, Clock.systemUTC());
    }

    // Visible for testing
    static Logger get(String name, Handler handler) {
        return new Logger(name, handler, AttrChain.EMPTY, Clock.systemUTC());
    }

    static Logger get(String name, Handler handler, Clock clock) {
        return new Logger(name, handler, AttrChain.EMPTY, clock);
    }

    Logger(String name, Handler handler, AttrChain contextAttrs, Clock clock) {
        this.name = name;
        this.handler = handler;
        this.contextAttrs = contextAttrs;
        this.clock = clock;
    }

    /**
     * Returns a {@link Builder} for constructing a child logger with multiple
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
    public Builder with() {
        return new Builder(this);
    }

    /**
     * A builder for constructing a child logger with multiple context attributes
     * batched into a single chain node, avoiding intermediate Logger instances.
     */
    public static final class Builder {
        private final Logger parent;
        private final List<Attr> attrs = new ArrayList<>();
        private AttrChain inherited = AttrChain.EMPTY;

        Builder(Logger parent) {
            this.parent = parent;
        }

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
        public Builder ctx(Logger other) {
            if (!other.contextAttrs.isEmpty()) {
                inherited = other.contextAttrs.withPrefix(inherited);
            }
            return this;
        }

        /**
         * Adds a context attribute to the child logger being built.
         *
         * @param key   the attribute name
         * @param value the attribute value
         * @return this builder, for chaining
         */
        public Builder attr(String key, Object value) {
            attrs.add(Attr.of(key, value));
            return this;
        }

        /**
         * Builds and returns the child logger with all accumulated attributes
         * as a single chain node.
         *
         * @return a new {@code Logger} with the added context attributes
         */
        public Logger build() {
            AttrChain chain = parent.contextAttrs;
            if (!inherited.isEmpty()) {
                chain = chain.withPrefix(inherited);
            }
            if (!attrs.isEmpty()) {
                chain = chain.with(attrs);
            }
            if (chain == parent.contextAttrs) {
                return parent;
            }
            return new Logger(parent.name, parent.handler, chain, parent.clock);
        }
    }

    // --- Logging methods ---
    // Each level has two overloads:
    //   info(msg)  — logs a plain message, no allocations beyond the record
    //   info()     — returns a fluent Event builder for attrs, exceptions, timing

    /**
     * Logs a message at TRACE level. No-op if TRACE is disabled.
     * For structured attributes use {@link #trace()}.
     */
    public void trace(String msg) {
        if (!handler.isEnabled(name, Level.TRACE)) return;
        handler.handle(buildRecord(Level.TRACE, msg));
    }

    /** Returns a fluent TRACE-level event builder. No-op if TRACE is disabled. */
    public Event trace() {
        if (!handler.isEnabled(name, Level.TRACE)) return NoopEvent.INSTANCE;
        return new EventImpl(this, Level.TRACE, clock);
    }

    /**
     * Logs a message at DEBUG level. No-op if DEBUG is disabled.
     * For structured attributes use {@link #debug()}.
     */
    public void debug(String msg) {
        if (!handler.isEnabled(name, Level.DEBUG)) return;
        handler.handle(buildRecord(Level.DEBUG, msg));
    }

    /** Returns a fluent DEBUG-level event builder. No-op if DEBUG is disabled. */
    public Event debug() {
        if (!handler.isEnabled(name, Level.DEBUG)) return NoopEvent.INSTANCE;
        return new EventImpl(this, Level.DEBUG, clock);
    }

    /**
     * Logs a message at INFO level. No-op if INFO is disabled.
     * For structured attributes use {@link #info()}.
     */
    public void info(String msg) {
        if (!handler.isEnabled(name, Level.INFO)) return;
        handler.handle(buildRecord(Level.INFO, msg));
    }

    /** Returns a fluent INFO-level event builder. No-op if INFO is disabled. */
    public Event info() {
        if (!handler.isEnabled(name, Level.INFO)) return NoopEvent.INSTANCE;
        return new EventImpl(this, Level.INFO, clock);
    }

    /**
     * Logs a message at WARN level. No-op if WARN is disabled.
     * For structured attributes use {@link #warn()}.
     */
    public void warn(String msg) {
        if (!handler.isEnabled(name, Level.WARN)) return;
        handler.handle(buildRecord(Level.WARN, msg));
    }

    /** Returns a fluent WARN-level event builder. No-op if WARN is disabled. */
    public Event warn() {
        if (!handler.isEnabled(name, Level.WARN)) return NoopEvent.INSTANCE;
        return new EventImpl(this, Level.WARN, clock);
    }

    /**
     * Logs a message at ERROR level. No-op if ERROR is disabled.
     * For structured attributes use {@link #error()}.
     */
    public void error(String msg) {
        if (!handler.isEnabled(name, Level.ERROR)) return;
        handler.handle(buildRecord(Level.ERROR, msg));
    }

    /** Returns a fluent ERROR-level event builder. No-op if ERROR is disabled. */
    public Event error() {
        if (!handler.isEnabled(name, Level.ERROR)) return NoopEvent.INSTANCE;
        return new EventImpl(this, Level.ERROR, clock);
    }

    /**
     * Checks whether the given level is enabled for this logger.
     *
     * @param level the level to check
     * @return {@code true} if a log at this level would be emitted
     */
    public boolean isEnabled(Level level) {
        return handler.isEnabled(name, level);
    }

    // --- Package-private helpers for EventImpl ---

    String name() {
        return name;
    }

    Handler handler() {
        return handler;
    }

    Iterable<Attr> mergeAttrs(List<Attr> eventAttrs) {
        if (eventAttrs == null || eventAttrs.isEmpty()) {
            return contextAttrs;
        }
        if (contextAttrs.isEmpty()) {
            return eventAttrs;
        }
        // Create a child chain node with the event attrs, backed by context chain
        return contextAttrs.with(eventAttrs);
    }

    // --- Private helpers ---

    private LogRecord buildRecord(Level level, String msg) {
        return new LogRecord(name, level, msg, contextAttrs, null, clock.instant(), null);
    }
}
