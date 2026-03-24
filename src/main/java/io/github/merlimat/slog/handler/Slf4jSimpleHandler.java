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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link Handler} implementation for SLF4J Simple logger.
 *
 * <p>Since {@code slf4j-simple} does not render MDC values, this handler
 * inlines structured attributes directly into the log message as
 * {@code key=value} pairs.
 *
 * <p>This handler is selected automatically when {@code slf4j-simple} is
 * detected on the classpath. For backends that support MDC (Logback, etc.),
 * use {@link Slf4jHandler} instead.
 */
public class Slf4jSimpleHandler implements Handler {

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
        String msg = formatMessage(record);
        Throwable t = record.throwable();
        switch (record.level()) {
            case TRACE -> { if (t != null) logger.trace(msg, t); else logger.trace(msg); }
            case DEBUG -> { if (t != null) logger.debug(msg, t); else logger.debug(msg); }
            case INFO ->  { if (t != null) logger.info(msg, t);  else logger.info(msg);  }
            case WARN ->  { if (t != null) logger.warn(msg, t);  else logger.warn(msg);  }
            case ERROR -> { if (t != null) logger.error(msg, t); else logger.error(msg); }
        }
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

    private Logger getLogger(String name) {
        return loggers.computeIfAbsent(name, LoggerFactory::getLogger);
    }
}
