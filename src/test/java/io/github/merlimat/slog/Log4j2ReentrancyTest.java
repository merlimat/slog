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
package io.github.merlimat.slog;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.Test;

/**
 * Verifies that a reentrant log call — user code logging through slog while an
 * emit is already in flight on the same thread, e.g. an attr supplier that
 * logs — does not corrupt the outer event. The pooled event and context map
 * belong to the outer emit; the nested one must fall back to fresh instances.
 */
class Log4j2ReentrancyTest {
    private static final String LOGGER_NAME = "io.github.merlimat.slog.ReentrancyTest";

    private static final class CapturingAppender extends AbstractAppender {
        final List<LogEvent> events = new CopyOnWriteArrayList<>();

        CapturingAppender() {
            super("ReentrancyCapture", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            events.add(event.toImmutable());
        }
    }

    @Test
    void supplierThatLogsDoesNotCorruptTheOuterEvent() {
        CapturingAppender appender = new CapturingAppender();
        appender.start();
        org.apache.logging.log4j.core.Logger log4jLogger =
                (org.apache.logging.log4j.core.Logger) LogManager.getLogger(LOGGER_NAME);
        log4jLogger.addAppender(appender);

        try {
            Logger log = Logger.get(LOGGER_NAME);

            log.info()
                    .attr("inner", (ThrowingSupplier<?>) () -> {
                        // Runs in the middle of the outer emit, on the same thread
                        log.warn().attr("nested", true).log("inner-message");
                        return "resolved";
                    })
                    .log("outer-message");

            assertEquals(2, appender.events.size());

            // The nested emit completes first
            LogEvent inner = appender.events.get(0);
            assertEquals(Level.WARN, inner.getLevel());
            assertEquals("inner-message", inner.getMessage().getFormattedMessage());
            assertEquals(true, inner.getContextData().getValue("nested"));
            assertEquals(LOGGER_NAME, inner.getLoggerName());

            LogEvent outer = appender.events.get(1);
            assertEquals(Level.INFO, outer.getLevel(), "outer level must not be clobbered");
            assertEquals("outer-message", outer.getMessage().getFormattedMessage(),
                    "outer message must not be clobbered by the nested emit");
            assertEquals("resolved", outer.getContextData().getValue("inner"));
            assertNull(outer.getContextData().getValue("nested"),
                    "nested attrs must not bleed into the outer event");
        } finally {
            log4jLogger.removeAppender(appender);
            appender.stop();
        }
    }

    @Test
    void pooledStateIsReusedAfterReentrantEmit() {
        CapturingAppender appender = new CapturingAppender();
        appender.start();
        org.apache.logging.log4j.core.Logger log4jLogger =
                (org.apache.logging.log4j.core.Logger) LogManager.getLogger(LOGGER_NAME);
        log4jLogger.addAppender(appender);

        try {
            Logger log = Logger.get(LOGGER_NAME);

            log.info()
                    .attr("v", (ThrowingSupplier<?>) () -> {
                        log.info("nested");
                        return "x";
                    })
                    .log("first");
            // The guard must have been released: this emit reuses the pooled state
            log.info().attr("k", "v2").log("second");

            assertEquals(3, appender.events.size());
            assertEquals("second", appender.events.get(2).getMessage().getFormattedMessage());
            assertEquals("v2", appender.events.get(2).getContextData().getValue("k"));
        } finally {
            log4jLogger.removeAppender(appender);
            appender.stop();
        }
    }
}
