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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.lookup.StrSubstitutor;
import org.apache.logging.log4j.util.ReadOnlyStringMap;
import org.junit.jupiter.api.Test;

/**
 * Regression test for <a href="https://github.com/merlimat/slog/pull/3">PR #3</a>.
 *
 * <p>Log4j2 lookups such as {@code ${ctx:key}} — used by e.g. {@code RoutingAppender}
 * to dispatch events to routes — resolve to {@code ContextMapLookup.lookup}, which
 * calls {@code event.getContextData().getValue(key)} without null-checking. slog
 * must therefore always set non-null context data on emitted events, even when the
 * event has no attached attributes.
 *
 * <p>Before the fix, {@code Log4j2Logger.buildContextData} returned {@code null}
 * when the event had no context, no attrs and no duration, which propagated into
 * {@code MutableLogEvent} and caused an NPE whenever a downstream layout/appender
 * used a {@code ${ctx:...}} substitution on the event.
 */
class Log4j2ContextDataNpeTest {
    private static final String LOGGER_NAME = "io.github.merlimat.slog.NpeTest";

    /**
     * Capturing appender that records the event's context-data reference at
     * {@code append()} time and runs a {@code ${ctx:key}} substitution against
     * the event, recording any NPE that escapes the lookup.
     *
     * <p>All capture is done inline (copying into immutable snapshots) so that
     * subsequent reuse of slog's pooled {@code MutableLogEvent}/{@code StringMap}
     * cannot race with the assertions.
     */
    private static final class CapturingAppender extends AbstractAppender {
        final AtomicBoolean sawNullContextData = new AtomicBoolean();
        final AtomicBoolean lookupThrewNpe = new AtomicBoolean();
        final AtomicReference<Map<String, String>> snapshot = new AtomicReference<>();
        private final StrSubstitutor substitutor;

        CapturingAppender(StrSubstitutor substitutor) {
            super("NpeCapturing", null, null, true, Property.EMPTY_ARRAY);
            this.substitutor = substitutor;
        }

        @Override
        public void append(LogEvent event) {
            ReadOnlyStringMap data = event.getContextData();
            if (data == null) {
                sawNullContextData.set(true);
                snapshot.set(null);
            } else {
                snapshot.set(new HashMap<>(data.toMap()));
            }

            // This is the real-world bug path: StrSubstitutor routes ${ctx:key}
            // to ContextMapLookup.lookup(event, "key"), which does
            //     event.getContextData().getValue(key)
            // with no null-check. Pre-fix, this NPEs whenever the event has no
            // context data attached.
            try {
                substitutor.replace(event, "${ctx:someKey}");
            } catch (NullPointerException e) {
                lookupThrewNpe.set(true);
            }
        }
    }

    private CapturingAppender installAppender() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();

        CapturingAppender appender = new CapturingAppender(config.getStrSubstitutor());
        appender.start();

        org.apache.logging.log4j.core.Logger logger =
                (org.apache.logging.log4j.core.Logger) LogManager.getLogger(LOGGER_NAME);
        logger.addAppender(appender);
        return appender;
    }

    private void uninstallAppender(CapturingAppender appender) {
        org.apache.logging.log4j.core.Logger logger =
                (org.apache.logging.log4j.core.Logger) LogManager.getLogger(LOGGER_NAME);
        logger.removeAppender(appender);
        appender.stop();
    }

    @Test
    void plainInfoLog_contextDataIsNonNull() {
        // No context, no attrs, no duration — the case that made buildContextData()
        // return null before the fix.
        CapturingAppender appender = installAppender();
        try {
            Logger.get(LOGGER_NAME).info("plain message");
        } finally {
            uninstallAppender(appender);
        }

        assertFalse(appender.sawNullContextData.get(),
                "event.getContextData() must never be null");
        assertFalse(appender.lookupThrewNpe.get(),
                "${ctx:key} substitution must not NPE on an event with no context");
        assertNotNull(appender.snapshot.get());
        assertTrue(appender.snapshot.get().isEmpty(),
                "snapshot should be empty for a log call with no attrs");
    }

    @Test
    void fluentBuilderWithoutAttrs_contextDataIsNonNull() {
        // Fluent builder invoked with NO .attr() calls and no duration.
        CapturingAppender appender = installAppender();
        try {
            Logger.get(LOGGER_NAME).info().log("no attrs");
        } finally {
            uninstallAppender(appender);
        }

        assertFalse(appender.sawNullContextData.get(),
                "event.getContextData() must never be null");
        assertFalse(appender.lookupThrewNpe.get(),
                "${ctx:key} substitution must not NPE");
        assertTrue(appender.snapshot.get().isEmpty());
    }

    @Test
    void derivedLoggerWithEmptyContext_contextDataIsNonNull() {
        // Derived logger whose context-attrs chain is empty (.with().build()).
        CapturingAppender appender = installAppender();
        try {
            Logger log = Logger.get(LOGGER_NAME).with().build();
            log.info("derived, still empty");
        } finally {
            uninstallAppender(appender);
        }

        assertFalse(appender.sawNullContextData.get(),
                "event.getContextData() must never be null");
        assertFalse(appender.lookupThrewNpe.get(),
                "${ctx:key} substitution must not NPE");
        assertTrue(appender.snapshot.get().isEmpty());
    }

    @Test
    void loggerWithContextAttrs_contextDataIsPopulated() {
        // Sanity check: the non-empty path still populates context data correctly.
        CapturingAppender appender = installAppender();
        try {
            Logger log = Logger.get(LOGGER_NAME).with().attr("foo", "bar").build();
            log.info("with context");
        } finally {
            uninstallAppender(appender);
        }

        assertFalse(appender.sawNullContextData.get());
        assertFalse(appender.lookupThrewNpe.get());
        Map<String, String> data = appender.snapshot.get();
        assertNotNull(data);
        assertEquals("bar", data.get("foo"));
    }
}
