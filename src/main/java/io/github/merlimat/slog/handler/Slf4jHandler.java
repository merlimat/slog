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
import org.slf4j.MDC;

/**
 * {@link Handler} implementation that delegates to SLF4J.
 *
 * <p>Structured attributes are placed into the SLF4J {@link MDC} for
 * the duration of each log call, making them available to any SLF4J-compatible
 * backend (Logback, etc.) via pattern layouts or JSON encoders.
 *
 * <p>This handler is the default fallback when Log4j2 is not on the classpath.
 */
public class Slf4jHandler implements Handler {
    /** Creates a new SLF4J handler. */
    public Slf4jHandler() {}

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
        var saved = MDC.getCopyOfContextMap();
        try {
            for (Attr attr : record.attrs()) {
                MDC.put(attr.key(), attr.valueAsString());
            }
            if (record.duration() != null) {
                MDC.put("durationMs", String.valueOf(record.duration().toMillis()));
            }

            String msg = record.message();
            Throwable t = record.throwable();
            switch (record.level()) {
                case TRACE -> { if (t != null) logger.trace(msg, t); else logger.trace(msg); }
                case DEBUG -> { if (t != null) logger.debug(msg, t); else logger.debug(msg); }
                case INFO ->  { if (t != null) logger.info(msg, t);  else logger.info(msg);  }
                case WARN ->  { if (t != null) logger.warn(msg, t);  else logger.warn(msg);  }
                case ERROR -> { if (t != null) logger.error(msg, t); else logger.error(msg); }
            }
        } finally {
            if (saved != null) {
                MDC.setContextMap(saved);
            } else {
                MDC.clear();
            }
        }
    }

    private Logger getLogger(String name) {
        return loggers.computeIfAbsent(name, LoggerFactory::getLogger);
    }
}
