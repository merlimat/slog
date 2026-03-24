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
package io.github.merlimat.slog.handler;

import io.github.merlimat.slog.Attr;
import io.github.merlimat.slog.Handler;
import io.github.merlimat.slog.Level;
import io.github.merlimat.slog.LogRecord;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

/**
 * {@link Handler} implementation that delegates to Log4j2.
 *
 * <p>Structured attributes are placed into the Log4j2 {@link ThreadContext}
 * for the duration of each log call. When using {@code JsonLayout} with
 * {@code properties="true"}, each attribute appears as an individual field
 * inside the {@code contextMap} JSON object.
 *
 * <p>This handler is selected automatically when {@code org.apache.logging.log4j.LogManager}
 * is present on the classpath.
 */
public class Log4j2Handler implements Handler {
    private final ConcurrentHashMap<String, Logger> loggers = new ConcurrentHashMap<>();

    @Override
    public boolean isEnabled(String loggerName, Level level) {
        Logger logger = getLogger(loggerName);
        return switch (level) {
            case TRACE -> logger.isTraceEnabled();
            case DEBUG -> logger.isDebugEnabled();
            case INFO -> logger.isInfoEnabled();
            case WARN -> logger.isWarnEnabled();
            case ERROR -> logger.isErrorEnabled();
        };
    }

    @Override
    public void handle(LogRecord record) {
        Logger logger = getLogger(record.loggerName());
        try {
            for (Attr attr : record.attrs()) {
                ThreadContext.put(attr.key(), attr.valueAsString());
            }
            if (record.duration() != null) {
                ThreadContext.put("durationMs", String.valueOf(record.duration().toMillis()));
            }

            org.apache.logging.log4j.Level log4jLevel = toLog4j2Level(record.level());
            String msg = record.message();
            if (record.throwable() != null) {
                logger.log(log4jLevel, msg, record.throwable());
            } else {
                logger.log(log4jLevel, msg);
            }
        } finally {
            ThreadContext.clearMap();
        }
    }

    private Logger getLogger(String name) {
        return loggers.computeIfAbsent(name, LogManager::getLogger);
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
