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
import java.util.List;
import java.util.Set;
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

    // --- Factory for tests ---

    static Logger forTest(String name, Set<Level> enabledLevels, List<LogRecord> sink) {
        return new TestLogger(name, enabledLevels, sink, AttrChain.EMPTY, Clock.systemUTC());
    }

    static Logger forTest(String name, Set<Level> enabledLevels, List<LogRecord> sink, Clock clock) {
        return new TestLogger(name, enabledLevels, sink, AttrChain.EMPTY, clock);
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

    protected abstract void emit(LogRecord record);

    abstract Logger derive(AttrChain contextAttrs);

    // --- Logging methods ---

    @Override
    public void trace(String msg) {
        if (!isTraceEnabled()) return;
        emit(buildRecord(Level.TRACE, msg));
    }

    @Override
    public void tracef(String format, Object... args) {
        if (!isTraceEnabled()) return;
        emit(buildRecord(Level.TRACE, String.format(format, args)));
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
        emit(buildRecord(Level.DEBUG, msg));
    }

    @Override
    public void debugf(String format, Object... args) {
        if (!isDebugEnabled()) return;
        emit(buildRecord(Level.DEBUG, String.format(format, args)));
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
        emit(buildRecord(Level.INFO, msg));
    }

    @Override
    public void infof(String format, Object... args) {
        if (!isInfoEnabled()) return;
        emit(buildRecord(Level.INFO, String.format(format, args)));
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
        emit(buildRecord(Level.WARN, msg));
    }

    @Override
    public void warnf(String format, Object... args) {
        if (!isWarnEnabled()) return;
        emit(buildRecord(Level.WARN, String.format(format, args)));
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
        emit(buildRecord(Level.ERROR, msg));
    }

    @Override
    public void errorf(String format, Object... args) {
        if (!isErrorEnabled()) return;
        emit(buildRecord(Level.ERROR, String.format(format, args)));
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

    // --- Internal helpers ---

    Iterable<Attr> mergeAttrs(List<Attr> eventAttrs) {
        if (eventAttrs == null || eventAttrs.isEmpty()) {
            return contextAttrs;
        }
        if (contextAttrs.isEmpty()) {
            return eventAttrs;
        }
        return contextAttrs.with(eventAttrs);
    }

    private LogRecord buildRecord(Level level, String msg) {
        return new LogRecord(name, level, msg, contextAttrs, null, null, FQCN);
    }

    // --- Test-only Logger ---

    static final class TestLogger extends BaseLogger {
        private final Set<Level> enabledLevels;
        private final List<LogRecord> sink;

        TestLogger(String name, Set<Level> enabledLevels, List<LogRecord> sink,
                   AttrChain contextAttrs, Clock clock) {
            super(name, contextAttrs, clock);
            this.enabledLevels = enabledLevels;
            this.sink = sink;
        }

        @Override protected boolean isTraceEnabled() { return enabledLevels.contains(Level.TRACE); }
        @Override protected boolean isDebugEnabled() { return enabledLevels.contains(Level.DEBUG); }
        @Override protected boolean isInfoEnabled()  { return enabledLevels.contains(Level.INFO); }
        @Override protected boolean isWarnEnabled()  { return enabledLevels.contains(Level.WARN); }
        @Override protected boolean isErrorEnabled() { return enabledLevels.contains(Level.ERROR); }

        @Override
        protected void emit(LogRecord record) { sink.add(record); }

        @Override
        public Logger derive(AttrChain contextAttrs) {
            return new TestLogger(name(), enabledLevels, sink, contextAttrs, clock);
        }
    }
}
