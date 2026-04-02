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

import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.spi.ExtendedLogger;

/**
 * Logger implementation bound directly to a Log4j2 {@link ExtendedLogger},
 * eliminating handler indirection on every call.
 */
final class Log4j2Logger extends BaseLogger {
    private final ExtendedLogger log4j;

    Log4j2Logger(String name, AttrChain contextAttrs, Clock clock) {
        super(name, contextAttrs, clock);
        this.log4j = (ExtendedLogger) LogManager.getLogger(name);
    }

    private Log4j2Logger(String name, ExtendedLogger log4j, AttrChain contextAttrs, Clock clock) {
        super(name, contextAttrs, clock);
        this.log4j = log4j;
    }

    @Override
    protected boolean isTraceEnabled() { return log4j.isTraceEnabled(); }

    @Override
    protected boolean isDebugEnabled() { return log4j.isDebugEnabled(); }

    @Override
    protected boolean isInfoEnabled() { return log4j.isInfoEnabled(); }

    @Override
    protected boolean isWarnEnabled() { return log4j.isWarnEnabled(); }

    @Override
    protected boolean isErrorEnabled() { return log4j.isErrorEnabled(); }

    @Override
    protected void emit(LogRecord record) {
        boolean hasContext = record.hasContext();
        Map<String, String> savedCtx =
                ThreadContext.isEmpty() ? null : ThreadContext.getImmutableContext();

        try {
            for (Attr attr : record.attrs()) {
                ThreadContext.put(attr.key(), attr.valueAsString());
            }
            if (record.duration() != null) {
                ThreadContext.put("durationMs", String.valueOf(record.duration().toMillis()));
            }

            org.apache.logging.log4j.Level log4jLevel = toLog4j2Level(record.level());
            var message = log4j.getMessageFactory().newMessage(record.message());
            log4j.logMessage(record.callerFqcn(), log4jLevel, null, message, record.throwable());
        } finally {
            if (hasContext) {
                ThreadContext.clearMap();
            }

            if (savedCtx != null) {
                ThreadContext.putAll(savedCtx);
            }
        }
    }

    @Override
    public Logger derive(AttrChain contextAttrs) {
        return new Log4j2Logger(name(), log4j, contextAttrs, clock);
    }

    private static org.apache.logging.log4j.Level toLog4j2Level(Level level) {
        return switch (level) {
            case TRACE -> org.apache.logging.log4j.Level.TRACE;
            case DEBUG -> org.apache.logging.log4j.Level.DEBUG;
            case INFO -> org.apache.logging.log4j.Level.INFO;
            case WARN -> org.apache.logging.log4j.Level.WARN;
            case ERROR -> org.apache.logging.log4j.Level.ERROR;
        };
    }
}
