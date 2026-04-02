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
import java.util.function.Supplier;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Logger implementation bound directly to an SLF4J {@link org.slf4j.Logger},
 * eliminating handler indirection on every call.
 */
final class Slf4jLogger extends BaseLogger {
    private final org.slf4j.Logger slf4j;

    Slf4jLogger(String name, AttrChain contextAttrs, Clock clock) {
        super(name, contextAttrs, clock);
        this.slf4j = LoggerFactory.getLogger(name);
    }

    private Slf4jLogger(String name, org.slf4j.Logger slf4j, AttrChain contextAttrs, Clock clock) {
        super(name, contextAttrs, clock);
        this.slf4j = slf4j;
    }

    @Override
    protected boolean isTraceEnabled() { return slf4j.isTraceEnabled(); }

    @Override
    protected boolean isDebugEnabled() { return slf4j.isDebugEnabled(); }

    @Override
    protected boolean isInfoEnabled() { return slf4j.isInfoEnabled(); }

    @Override
    protected boolean isWarnEnabled() { return slf4j.isWarnEnabled(); }

    @Override
    protected boolean isErrorEnabled() { return slf4j.isErrorEnabled(); }

    @Override
    protected void emit(String loggerName, Level level, String message,
                        AttrChain contextAttrs,
                        String[] eventKeys, Object[] eventValues, int eventAttrCount,
                        Throwable throwable, Duration duration, String callerFqcn) {
        if (hasContext(contextAttrs, eventAttrCount, duration)) {
            emitWithMdc(level, message, contextAttrs, eventKeys, eventValues, eventAttrCount,
                    throwable, duration);
        } else {
            emitPlain(level, message, throwable);
        }
    }

    private void emitPlain(Level level, String msg, Throwable t) {
        switch (level) {
            case TRACE -> { if (t != null) slf4j.trace(msg, t); else slf4j.trace(msg); }
            case DEBUG -> { if (t != null) slf4j.debug(msg, t); else slf4j.debug(msg); }
            case INFO ->  { if (t != null) slf4j.info(msg, t);  else slf4j.info(msg);  }
            case WARN ->  { if (t != null) slf4j.warn(msg, t);  else slf4j.warn(msg);  }
            case ERROR -> { if (t != null) slf4j.error(msg, t); else slf4j.error(msg); }
        }
    }

    private void emitWithMdc(Level level, String msg, AttrChain contextAttrs,
                             String[] eventKeys, Object[] eventValues, int eventAttrCount,
                             Throwable throwable, Duration duration) {
        var saved = MDC.getCopyOfContextMap();
        try {
            for (Attr attr : contextAttrs) {
                MDC.put(attr.key(), attr.valueAsString());
            }
            for (int i = 0; i < eventAttrCount; i++) {
                Object v = eventValues[i];
                Object resolved = v instanceof Supplier<?> s ? s.get() : v;
                MDC.put(eventKeys[i], resolved == null ? null : String.valueOf(resolved));
            }
            if (duration != null) {
                MDC.put("durationMs", String.valueOf(duration.toMillis()));
            }
            emitPlain(level, msg, throwable);
        } finally {
            if (saved != null) {
                MDC.setContextMap(saved);
            } else {
                MDC.clear();
            }
        }
    }

    @Override
    public Logger derive(AttrChain contextAttrs) {
        return new Slf4jLogger(name(), slf4j, contextAttrs, clock);
    }
}
