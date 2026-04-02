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

import io.github.merlimat.slog.Logger;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Test-only logger that captures emitted records into a sink list.
 */
final class TestLogger extends BaseLogger {
    private final Set<Level> enabledLevels;
    private final List<LogRecord> sink;

    TestLogger(String name, Set<Level> enabledLevels, List<LogRecord> sink,
               AttrChain contextAttrs, Clock clock) {
        super(name, contextAttrs, clock);
        this.enabledLevels = enabledLevels;
        this.sink = sink;
    }

    static Logger create(String name, Set<Level> enabledLevels, List<LogRecord> sink) {
        return new TestLogger(name, enabledLevels, sink, AttrChain.EMPTY, Clock.systemUTC());
    }

    static Logger create(String name, Set<Level> enabledLevels, List<LogRecord> sink, Clock clock) {
        return new TestLogger(name, enabledLevels, sink, AttrChain.EMPTY, clock);
    }

    @Override protected boolean isTraceEnabled() { return enabledLevels.contains(Level.TRACE); }
    @Override protected boolean isDebugEnabled() { return enabledLevels.contains(Level.DEBUG); }
    @Override protected boolean isInfoEnabled()  { return enabledLevels.contains(Level.INFO); }
    @Override protected boolean isWarnEnabled()  { return enabledLevels.contains(Level.WARN); }
    @Override protected boolean isErrorEnabled() { return enabledLevels.contains(Level.ERROR); }

    @Override
    protected void emit(String loggerName, Level level, String message,
                        AttrChain contextAttrs,
                        String[] eventKeys, Object[] eventValues, int eventAttrCount,
                        Throwable throwable, Duration duration, String callerFqcn) {
        // Reconstruct a snapshot list of Attr for test assertions
        var attrs = new ArrayList<Attr>();
        for (Attr attr : contextAttrs) {
            attrs.add(attr);
        }
        for (int i = 0; i < eventAttrCount; i++) {
            Object v = eventValues[i];
            attrs.add(new Attr(eventKeys[i], v instanceof Supplier<?> s ? s.get() : v));
        }
        sink.add(new LogRecord(loggerName, level, message, attrs, throwable, duration, callerFqcn));
    }

    @Override
    public Logger derive(AttrChain contextAttrs) {
        return new TestLogger(name(), enabledLevels, sink, contextAttrs, clock);
    }
}
