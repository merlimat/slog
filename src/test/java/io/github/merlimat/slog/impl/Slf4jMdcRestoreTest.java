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

import static org.junit.jupiter.api.Assertions.*;

import io.github.merlimat.slog.Logger;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * Verifies the SLF4J handler's MDC handling: attributes are visible to the
 * backend for the duration of the call, and afterwards only the touched keys
 * are restored — ambient MDC entries survive, overlapping keys get their prior
 * values back, and staged keys do not leak.
 *
 * <p>SLF4J is bridged to log4j2 in the test classpath, so what the backend saw
 * during the call is asserted through the captured log4j2 event's context data.
 */
class Slf4jMdcRestoreTest {
    private static final String LOGGER_NAME = "io.github.merlimat.slog.impl.Slf4jMdcTest";

    private static final class CapturingAppender extends AbstractAppender {
        final List<LogEvent> events = new CopyOnWriteArrayList<>();

        CapturingAppender() {
            super("Slf4jMdcCapture", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            events.add(event.toImmutable());
        }
    }

    private final CapturingAppender appender = new CapturingAppender();
    private org.apache.logging.log4j.core.Logger log4jLogger;

    @BeforeEach
    void setup() {
        MDC.clear();
        appender.start();
        log4jLogger = (org.apache.logging.log4j.core.Logger) LogManager.getLogger(LOGGER_NAME);
        log4jLogger.addAppender(appender);
    }

    @AfterEach
    void cleanup() {
        log4jLogger.removeAppender(appender);
        appender.stop();
        MDC.clear();
    }

    private static Logger newSlf4jLogger() {
        return new Slf4jLogger(LOGGER_NAME, AttrChain.EMPTY, Clock.systemUTC());
    }

    @Test
    void ambientMdcIsVisibleDuringEmitAndPreservedAfter() {
        MDC.put("requestId", "r-1");

        Logger log = newSlf4jLogger();
        log.info().attr("msgId", "m-1").log("hello");

        assertEquals(1, appender.events.size());
        LogEvent event = appender.events.get(0);
        assertEquals("r-1", event.getContextData().getValue("requestId"),
                "ambient MDC must be visible to the backend");
        assertEquals("m-1", event.getContextData().getValue("msgId"),
                "event attrs must be visible to the backend");

        assertEquals("r-1", MDC.get("requestId"), "ambient MDC must survive the call");
        assertNull(MDC.get("msgId"), "staged attrs must not leak into the caller MDC");
    }

    @Test
    void overlappingKeyIsRestoredToAmbientValue() {
        MDC.put("k", "ambient");

        Logger log = newSlf4jLogger();
        log.info().attr("k", "event").log("hello");

        assertEquals("event", appender.events.get(0).getContextData().getValue("k"),
                "the event value must win during the call");
        assertEquals("ambient", MDC.get("k"), "the ambient value must be restored");
    }

    @Test
    void duplicateKeysAcrossChainRestoreFirstPriorValue() {
        MDC.put("k", "ambient");

        Logger log = newSlf4jLogger().with().attr("k", "ctx").build();
        log.info().attr("k", "event").log("hello");

        // During the call, the last write wins (context attr first, then event attr)
        assertEquals("event", appender.events.get(0).getContextData().getValue("k"));
        // After the call, the original ambient value must win — not the intermediate "ctx"
        assertEquals("ambient", MDC.get("k"));
    }

    @Test
    void durationKeyDoesNotLeak() {
        Logger log = newSlf4jLogger();
        log.info().timed().log("hello");

        assertNotNull(appender.events.get(0).getContextData().getValue("durationMs"));
        assertNull(MDC.get("durationMs"));
    }

    @Test
    void manyAttrsGrowTheRestoreBufferCorrectly() {
        MDC.put("a3", "ambient");

        Logger log = newSlf4jLogger();
        var event = log.info();
        for (int i = 0; i < 20; i++) {
            event.attr("a" + i, "v" + i);
        }
        event.log("hello");

        var data = appender.events.get(0).getContextData();
        for (int i = 0; i < 20; i++) {
            assertEquals("v" + i, data.getValue("a" + i));
        }
        assertEquals("ambient", MDC.get("a3"), "overlapping ambient key must be restored");
        for (int i = 0; i < 20; i++) {
            if (i != 3) {
                assertNull(MDC.get("a" + i));
            }
        }
    }
}
