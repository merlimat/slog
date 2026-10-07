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
package io.github.merlimat.slog.impl;

import io.github.merlimat.slog.Event;
import io.github.merlimat.slog.Logger;
import java.time.Clock;
import java.util.function.Consumer;

/**
 * Abstract base implementation of {@link Logger} that provides the template-method
 * pattern for all logging operations. Concrete subclasses only need to implement
 * the level-check and emit methods for their specific backend.
 */
abstract class BaseLogger implements Logger {
    private static final String FQCN = BaseLogger.class.getName();

    private final String name;
    private final AttrChain contextAttrs;
    final Clock clock;

    protected BaseLogger(String name, AttrChain contextAttrs, Clock clock) {
        this.name = name;
        this.contextAttrs = contextAttrs;
        this.clock = clock;
    }

    // --- Accessors ---

    @Override
    public String name() {
        return name;
    }

    AttrChain contextAttrs() {
        return contextAttrs;
    }

    // --- Abstract methods for subclasses ---

    protected abstract boolean isTraceEnabled();
    protected abstract boolean isDebugEnabled();
    protected abstract boolean isInfoEnabled();
    protected abstract boolean isWarnEnabled();
    protected abstract boolean isErrorEnabled();

    /**
     * Emits a log record. {@code eventAttrs} holds the per-event attributes as
     * interleaved {@code {key, value, key, value, ...}} pairs and
     * {@code eventAttrCount} is the number of pairs. {@code durationNanos} is the
     * elapsed time recorded by {@link Event#timed()}, or {@code -1} when the event
     * was not timed.
     */
    protected abstract void emit(String loggerName, Level level, String message,
                                   AttrChain contextAttrs,
                                   Object[] eventAttrs, int eventAttrCount,
                                   Throwable throwable, long durationNanos, String callerFqcn);

    abstract Logger derive(AttrChain contextAttrs);

    /** Monotonic time source for {@link Event#timed()} durations. Overridable in tests. */
    long nanoTime() {
        return System.nanoTime();
    }

    static boolean hasContext(AttrChain contextAttrs, int eventAttrCount, long durationNanos) {
        return !contextAttrs.isEmpty() || eventAttrCount > 0 || durationNanos >= 0;
    }

    // --- Logging methods ---

    @Override
    public void trace(String msg) {
        if (!isTraceEnabled()) return;
        emit(name, Level.TRACE, msg, contextAttrs, null, 0, null, -1, FQCN);
    }

    @Override
    public void tracef(String format, Object... args) {
        if (!isTraceEnabled()) return;
        emit(name, Level.TRACE, String.format(format, args), contextAttrs, null, 0, null, -1, FQCN);
    }

    @Override
    public Event trace() {
        if (!isTraceEnabled()) return NoopEvent.INSTANCE;
        return new EventImpl(this, Level.TRACE, clock);
    }

    @Override
    public void trace(Consumer<Event> consumer) {
        if (!isTraceEnabled()) return;
        consumer.accept(new EventImpl(this, Level.TRACE, clock));
    }

    @Override
    public void debug(String msg) {
        if (!isDebugEnabled()) return;
        emit(name, Level.DEBUG, msg, contextAttrs, null, 0, null, -1, FQCN);
    }

    @Override
    public void debugf(String format, Object... args) {
        if (!isDebugEnabled()) return;
        emit(name, Level.DEBUG, String.format(format, args), contextAttrs, null, 0, null, -1, FQCN);
    }

    @Override
    public Event debug() {
        if (!isDebugEnabled()) return NoopEvent.INSTANCE;
        return new EventImpl(this, Level.DEBUG, clock);
    }

    @Override
    public void debug(Consumer<Event> consumer) {
        if (!isDebugEnabled()) return;
        consumer.accept(new EventImpl(this, Level.DEBUG, clock));
    }

    @Override
    public void info(String msg) {
        if (!isInfoEnabled()) return;
        emit(name, Level.INFO, msg, contextAttrs, null, 0, null, -1, FQCN);
    }

    @Override
    public void infof(String format, Object... args) {
        if (!isInfoEnabled()) return;
        emit(name, Level.INFO, String.format(format, args), contextAttrs, null, 0, null, -1, FQCN);
    }

    @Override
    public Event info() {
        if (!isInfoEnabled()) return NoopEvent.INSTANCE;
        return new EventImpl(this, Level.INFO, clock);
    }

    @Override
    public void info(Consumer<Event> consumer) {
        if (!isInfoEnabled()) return;
        consumer.accept(new EventImpl(this, Level.INFO, clock));
    }

    @Override
    public void warn(String msg) {
        if (!isWarnEnabled()) return;
        emit(name, Level.WARN, msg, contextAttrs, null, 0, null, -1, FQCN);
    }

    @Override
    public void warnf(String format, Object... args) {
        if (!isWarnEnabled()) return;
        emit(name, Level.WARN, String.format(format, args), contextAttrs, null, 0, null, -1, FQCN);
    }

    @Override
    public Event warn() {
        if (!isWarnEnabled()) return NoopEvent.INSTANCE;
        return new EventImpl(this, Level.WARN, clock);
    }

    @Override
    public void warn(Consumer<Event> consumer) {
        if (!isWarnEnabled()) return;
        consumer.accept(new EventImpl(this, Level.WARN, clock));
    }

    @Override
    public void error(String msg) {
        if (!isErrorEnabled()) return;
        emit(name, Level.ERROR, msg, contextAttrs, null, 0, null, -1, FQCN);
    }

    @Override
    public void errorf(String format, Object... args) {
        if (!isErrorEnabled()) return;
        emit(name, Level.ERROR, String.format(format, args), contextAttrs, null, 0, null, -1, FQCN);
    }

    @Override
    public Event error() {
        if (!isErrorEnabled()) return NoopEvent.INSTANCE;
        return new EventImpl(this, Level.ERROR, clock);
    }

    @Override
    public void error(Consumer<Event> consumer) {
        if (!isErrorEnabled()) return;
        consumer.accept(new EventImpl(this, Level.ERROR, clock));
    }

}
