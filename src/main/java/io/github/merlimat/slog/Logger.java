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
 * Logger slog = SLog.getLogger(Producer.class)
 *     .with("topic", topicName)
 *     .with("clientAddr", remoteAddr);
 *
 * // All subsequent logs from this logger include topic and clientAddr
 * slog.info("Message published", "msgId", id, "size", payload.length);
 * }</pre>
 *
 * <p><b>Zero overhead when disabled:</b> All logging methods check
 * {@link Handler#isEnabled} before constructing any objects. The fluent
 * {@code at*()} methods return a no-op {@link Event} singleton when the
 * level is disabled.
 */
public class Logger {
    private final String name;
    private final Handler handler;
    private final AttrChain contextAttrs;
    private final Clock clock;

    Logger(String name, Handler handler, AttrChain contextAttrs, Clock clock) {
        this.name = name;
        this.handler = handler;
        this.contextAttrs = contextAttrs;
        this.clock = clock;
    }

    /**
     * Returns a new Logger with an additional context attribute. The original logger
     * is unchanged. Parent attributes are shared, not copied.
     *
     * @param key   the attribute name
     * @param value the attribute value
     * @return a new {@code Logger} with the added context attribute
     */
    public Logger with(String key, Object value) {
        return new Logger(name, handler, contextAttrs.with(Attr.of(key, value)), clock);
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

        Builder(Logger parent) {
            this.parent = parent;
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
            if (attrs.isEmpty()) {
                return parent;
            }
            return new Logger(parent.name, parent.handler,
                    parent.contextAttrs.with(attrs), parent.clock);
        }
    }

    // --- Message-only overloads (no allocation beyond the record) ---

    /** Logs a message at TRACE level. No-op if TRACE is disabled. */
    public void trace(String msg) {
        if (!handler.isEnabled(name, Level.TRACE)) return;
        handler.handle(buildRecord(Level.TRACE, msg, null));
    }

    /** Logs a message at DEBUG level. No-op if DEBUG is disabled. */
    public void debug(String msg) {
        if (!handler.isEnabled(name, Level.DEBUG)) return;
        handler.handle(buildRecord(Level.DEBUG, msg, null));
    }

    /** Logs a message at INFO level. No-op if INFO is disabled. */
    public void info(String msg) {
        if (!handler.isEnabled(name, Level.INFO)) return;
        handler.handle(buildRecord(Level.INFO, msg, null));
    }

    /** Logs a message at WARN level. No-op if WARN is disabled. */
    public void warn(String msg) {
        if (!handler.isEnabled(name, Level.WARN)) return;
        handler.handle(buildRecord(Level.WARN, msg, null));
    }

    /** Logs a message at ERROR level. No-op if ERROR is disabled. */
    public void error(String msg) {
        if (!handler.isEnabled(name, Level.ERROR)) return;
        handler.handle(buildRecord(Level.ERROR, msg, null));
    }

    // --- Message + inline key-value pairs ---

    /**
     * Logs a message at TRACE level with inline key-value pairs.
     *
     * @param msg the log message
     * @param kvs alternating key-value pairs: {@code "key1", value1, "key2", value2, ...}
     */
    public void trace(String msg, Object... kvs) {
        if (!handler.isEnabled(name, Level.TRACE)) return;
        handler.handle(buildRecord(Level.TRACE, msg, kvs, null));
    }

    /** Logs a message at DEBUG level with inline key-value pairs. See {@link #trace(String, Object...)}. */
    public void debug(String msg, Object... kvs) {
        if (!handler.isEnabled(name, Level.DEBUG)) return;
        handler.handle(buildRecord(Level.DEBUG, msg, kvs, null));
    }

    /** Logs a message at INFO level with inline key-value pairs. See {@link #trace(String, Object...)}. */
    public void info(String msg, Object... kvs) {
        if (!handler.isEnabled(name, Level.INFO)) return;
        handler.handle(buildRecord(Level.INFO, msg, kvs, null));
    }

    /** Logs a message at WARN level with inline key-value pairs. See {@link #trace(String, Object...)}. */
    public void warn(String msg, Object... kvs) {
        if (!handler.isEnabled(name, Level.WARN)) return;
        handler.handle(buildRecord(Level.WARN, msg, kvs, null));
    }

    /** Logs a message at ERROR level with inline key-value pairs. See {@link #trace(String, Object...)}. */
    public void error(String msg, Object... kvs) {
        if (!handler.isEnabled(name, Level.ERROR)) return;
        handler.handle(buildRecord(Level.ERROR, msg, kvs, null));
    }

    // --- With exception ---

    /**
     * Logs a message at WARN level with an exception and optional key-value pairs.
     *
     * @param msg the log message
     * @param t   the throwable to attach
     * @param kvs alternating key-value pairs
     */
    public void warn(String msg, Throwable t, Object... kvs) {
        if (!handler.isEnabled(name, Level.WARN)) return;
        handler.handle(buildRecord(Level.WARN, msg, kvs, t));
    }

    /**
     * Logs a message at ERROR level with an exception and optional key-value pairs.
     *
     * @param msg the log message
     * @param t   the throwable to attach
     * @param kvs alternating key-value pairs
     */
    public void error(String msg, Throwable t, Object... kvs) {
        if (!handler.isEnabled(name, Level.ERROR)) return;
        handler.handle(buildRecord(Level.ERROR, msg, kvs, t));
    }

    // --- Fluent builder (returns NoopEvent when disabled) ---

    /**
     * Starts building a TRACE-level event. Returns a no-op if TRACE is disabled.
     *
     * @return an {@link Event} builder, or a no-op singleton
     */
    public Event atTrace() {
        if (!handler.isEnabled(name, Level.TRACE)) return NoopEvent.INSTANCE;
        return new EventImpl(this, Level.TRACE, clock);
    }

    /** Starts building a DEBUG-level event. Returns a no-op if DEBUG is disabled. */
    public Event atDebug() {
        if (!handler.isEnabled(name, Level.DEBUG)) return NoopEvent.INSTANCE;
        return new EventImpl(this, Level.DEBUG, clock);
    }

    /** Starts building an INFO-level event. Returns a no-op if INFO is disabled. */
    public Event atInfo() {
        if (!handler.isEnabled(name, Level.INFO)) return NoopEvent.INSTANCE;
        return new EventImpl(this, Level.INFO, clock);
    }

    /** Starts building a WARN-level event. Returns a no-op if WARN is disabled. */
    public Event atWarn() {
        if (!handler.isEnabled(name, Level.WARN)) return NoopEvent.INSTANCE;
        return new EventImpl(this, Level.WARN, clock);
    }

    /** Starts building an ERROR-level event. Returns a no-op if ERROR is disabled. */
    public Event atError() {
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

    private LogRecord buildRecord(Level level, String msg, Throwable throwable) {
        return new LogRecord(name, level, msg, contextAttrs, throwable, clock.instant(), null);
    }

    private LogRecord buildRecord(Level level, String msg, Object[] kvs, Throwable throwable) {
        Iterable<Attr> allAttrs = mergeKvs(kvs);
        return new LogRecord(name, level, msg, allAttrs, throwable, clock.instant(), null);
    }

    private Iterable<Attr> mergeKvs(Object[] kvs) {
        if (kvs == null || kvs.length == 0) {
            return contextAttrs;
        }

        List<Attr> eventAttrs = new ArrayList<>(kvs.length / 2);
        for (int i = 0; i < kvs.length - 1; i += 2) {
            eventAttrs.add(Attr.of(String.valueOf(kvs[i]), kvs[i + 1]));
        }
        if (contextAttrs.isEmpty()) {
            return eventAttrs;
        }
        return contextAttrs.with(eventAttrs);
    }
}
