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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.util.SortedArrayStringMap;
import org.apache.logging.log4j.util.StringMap;

/**
 * Logger implementation bound directly to a Log4j2 {@link ExtendedLogger},
 * eliminating handler indirection on every call.
 */
final class Log4j2Logger extends BaseLogger {
    private final org.apache.logging.log4j.core.Logger log4j;

    Log4j2Logger(String name, AttrChain contextAttrs, Clock clock) {
        super(name, contextAttrs, clock);
        this.log4j = (org.apache.logging.log4j.core.Logger) LogManager.getLogger(name);
    }

    private Log4j2Logger(String name, org.apache.logging.log4j.core.Logger log4j,
                          AttrChain contextAttrs, Clock clock) {
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
        org.apache.logging.log4j.Level log4jLevel = toLog4j2Level(record.level());

        StringMap contextData = buildContextData(record);
        Message message = log4j.getMessageFactory().newMessage(record.message());

        LogEvent event = Log4jLogEvent.newBuilder()
                .setLoggerName(record.loggerName())
                .setLoggerFqcn(record.callerFqcn())
                .setLevel(log4jLevel)
                .setMessage(message)
                .setThrown(record.throwable())
                .setContextData(contextData)
                .setThreadName(Thread.currentThread().getName())
                .build();

        LoggerConfig loggerConfig = log4j.get();
        loggerConfig.log(event);
    }

    private static final ThreadLocal<StringMap> THREAD_LOCAL_CTX =
            ThreadLocal.withInitial(SortedArrayStringMap::new);

    private static StringMap buildContextData(LogRecord record) {
        if (!record.hasContext()) {
            return null;
        }

        var map = THREAD_LOCAL_CTX.get();
        map.clear();
        for (Attr attr : record.attrs()) {
            map.putValue(attr.key(), attr.valueAsString());
        }
        if (record.duration() != null) {
            map.putValue("durationMs", String.valueOf(record.duration().toMillis()));
        }
        return map;
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
