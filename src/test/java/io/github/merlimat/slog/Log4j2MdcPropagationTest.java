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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.util.ReadOnlyStringMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@code Log4j2Logger} merges the caller thread's
 * {@link ThreadContext} (i.e. MDC) into the emitted {@link LogEvent}'s
 * {@code ContextData}.
 *
 * <p>Without this, layouts using {@code %X{key}} or appenders reading
 * {@code event.getContextData().getValue(key)} get nothing — even though the
 * thread had MDC entries set when the log call ran. This is the path the
 * standard log4j2 logger handles via {@code ContextDataInjector}; slog
 * builds the event manually and so must do the equivalent.
 */
class Log4j2MdcPropagationTest {
    private static final String LOGGER_NAME = "io.github.merlimat.slog.MdcPropagationTest";

    /**
     * Capturing appender that snapshots the event's context-data into an
     * immutable {@code HashMap} at {@code append()} time, so subsequent reuse
     * of slog's pooled {@code MutableLogEvent}/{@code StringMap} cannot race
     * with the assertions.
     */
    private static final class CapturingAppender extends AbstractAppender {
        final AtomicReference<Map<String, String>> lastSnapshot = new AtomicReference<>();

        CapturingAppender() {
            super("MdcCapturing", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            ReadOnlyStringMap data = event.getContextData();
            lastSnapshot.set(data == null ? null : new HashMap<>(data.toMap()));
        }
    }

    private CapturingAppender appender;

    @BeforeEach
    void setUp() {
        ThreadContext.clearAll();
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        appender = new CapturingAppender();
        appender.start();
        org.apache.logging.log4j.core.Logger logger =
                (org.apache.logging.log4j.core.Logger) LogManager.getLogger(LOGGER_NAME);
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        ThreadContext.clearAll();
        org.apache.logging.log4j.core.Logger logger =
                (org.apache.logging.log4j.core.Logger) LogManager.getLogger(LOGGER_NAME);
        logger.removeAppender(appender);
        appender.stop();
    }

    @Test
    void mdcOnlyPlainInfo_propagatesIntoEventContextData() {
        // MDC set on the thread; slog log call has no attrs.
        ThreadContext.put("requestId", "req-123");
        Logger.get(LOGGER_NAME).info("plain");

        Map<String, String> snap = appender.lastSnapshot.get();
        assertEquals("req-123", snap.get("requestId"),
                "ThreadContext entries must appear in event.getContextData()");
    }

    @Test
    void mdcOnlyFluentInfo_propagatesIntoEventContextData() {
        // MDC set on the thread; slog log uses fluent builder with no attrs.
        ThreadContext.put("requestId", "req-456");
        Logger.get(LOGGER_NAME).info().log("fluent, no attrs");

        Map<String, String> snap = appender.lastSnapshot.get();
        assertEquals("req-456", snap.get("requestId"));
    }

    @Test
    void mdcAndSlogAttrs_bothPresent_slogOverridesOnCollision() {
        ThreadContext.put("requestId", "req-789");
        ThreadContext.put("tenant", "acme");

        // Slog attr "requestId" overrides MDC; "ledgerId" is purely from slog.
        Logger.get(LOGGER_NAME).info()
                .attr("requestId", "from-slog")
                .attr("ledgerId", 42L)
                .log("event-attrs and mdc");

        Map<String, String> snap = appender.lastSnapshot.get();
        assertEquals("from-slog", snap.get("requestId"),
                "slog event attr should override MDC on key collision");
        assertEquals("acme", snap.get("tenant"),
                "MDC entries with no slog attr override should be preserved");
        assertEquals("42", snap.get("ledgerId"));
    }

    @Test
    void derivedLoggerContextAttr_overridesMdc() {
        // Logger-level (derived) context attrs should also override MDC on collision.
        ThreadContext.put("requestId", "from-mdc");

        Logger derived = Logger.get(LOGGER_NAME).with()
                .attr("requestId", "from-logger-context")
                .build();
        derived.info("derived");

        Map<String, String> snap = appender.lastSnapshot.get();
        assertEquals("from-logger-context", snap.get("requestId"));
    }

    @Test
    void mdcRestoredAfterLog() {
        // Slog must not pollute the thread's ThreadContext with its own attrs.
        ThreadContext.put("requestId", "req-orig");

        Logger.get(LOGGER_NAME).info()
                .attr("ledgerId", 7L)
                .log("attrs added then restored");

        // After the log call, ThreadContext should still have only the original entry.
        assertEquals("req-orig", ThreadContext.get("requestId"));
        assertNull(ThreadContext.get("ledgerId"),
                "slog event attrs must not leak into ThreadContext");
    }

    @Test
    void emptyMdc_emptyAttrs_eventContextDataIsEmpty() {
        // Fast path: no MDC, no slog attrs. Event context data should be empty
        // (and non-null — covered by Log4j2ContextDataNpeTest).
        Logger.get(LOGGER_NAME).info("plain, no mdc, no attrs");

        Map<String, String> snap = appender.lastSnapshot.get();
        assertFalse(snap == null);
        assertTrue(snap.isEmpty(), "ContextData must be empty when MDC and attrs are empty");
    }

    @Test
    void mdcRemovedBetweenCalls_secondEventDoesNotInheritFirst() {
        // Sanity: pooled thread-local StringMap must not retain stale MDC from a
        // previous call. We do two log calls; the first has MDC, the second doesn't.
        ThreadContext.put("requestId", "first");
        Logger.get(LOGGER_NAME).info("first call");
        Map<String, String> firstSnap = appender.lastSnapshot.get();
        assertEquals("first", firstSnap.get("requestId"));

        ThreadContext.clearAll();
        Logger.get(LOGGER_NAME).info("second call");
        Map<String, String> secondSnap = appender.lastSnapshot.get();
        assertTrue(secondSnap.isEmpty(),
                "Second call (with no MDC) must not see entries from the first call");
    }
}
