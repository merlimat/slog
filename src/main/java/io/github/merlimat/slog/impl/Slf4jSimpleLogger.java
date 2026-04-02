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
import org.slf4j.LoggerFactory;

/**
 * Logger implementation for SLF4J Simple, which does not support MDC.
 * Attributes are inlined directly into the log message.
 */
final class Slf4jSimpleLogger extends BaseLogger {
    private final org.slf4j.Logger slf4j;

    Slf4jSimpleLogger(String name, AttrChain contextAttrs, Clock clock) {
        super(name, contextAttrs, clock);
        this.slf4j = LoggerFactory.getLogger(name);
    }

    private Slf4jSimpleLogger(String name, org.slf4j.Logger slf4j, AttrChain contextAttrs, Clock clock) {
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
    protected void emit(LogRecord record) {
        String msg = formatMessage(record);
        Throwable t = record.throwable();
        switch (record.level()) {
            case TRACE -> { if (t != null) slf4j.trace(msg, t); else slf4j.trace(msg); }
            case DEBUG -> { if (t != null) slf4j.debug(msg, t); else slf4j.debug(msg); }
            case INFO ->  { if (t != null) slf4j.info(msg, t);  else slf4j.info(msg);  }
            case WARN ->  { if (t != null) slf4j.warn(msg, t);  else slf4j.warn(msg);  }
            case ERROR -> { if (t != null) slf4j.error(msg, t); else slf4j.error(msg); }
        }
    }

    @Override
    public Logger derive(AttrChain contextAttrs) {
        return new Slf4jSimpleLogger(name(), slf4j, contextAttrs, clock);
    }

    private static String formatMessage(LogRecord record) {
        var sb = new StringBuilder();
        sb.append(record.message());
        for (Attr attr : record.attrs()) {
            sb.append(' ').append(attr.key()).append('=').append(attr.valueAsString());
        }
        if (record.duration() != null) {
            sb.append(" durationMs=").append(record.duration().toMillis());
        }
        return sb.toString();
    }
}
