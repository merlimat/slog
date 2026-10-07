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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.async.AsyncLoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.util.ReadOnlyStringMap;
import org.junit.jupiter.api.Test;

/**
 * Verifies the full-async mode ({@code AsyncLoggerContextSelector}): slog must
 * route through {@code AsyncLogger} so that appenders run on the disruptor
 * background thread — not on the application thread — while structured attrs
 * still arrive in the event's context data.
 *
 * <p>Run by the {@code asyncLoggerTest} Gradle task, which sets
 * {@code -Dlog4j2.contextSelector=...AsyncLoggerContextSelector}.
 */
class AsyncLoggerModeTest {
    private static final String LOGGER_NAME = "io.github.merlimat.slog.AsyncModeTest";

    private static final class CapturingAppender extends AbstractAppender {
        record Capture(LogEvent event, String appendThread) {}

        final List<Capture> captures = new CopyOnWriteArrayList<>();

        CapturingAppender() {
            super("AsyncCapture", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            // The ring buffer slot is reused; snapshot before storing
            captures.add(new Capture(event.toImmutable(), Thread.currentThread().getName()));
        }
    }

    @Test
    void fullAsyncModeStaysAsyncAndCarriesAttrs() throws Exception {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        assertInstanceOf(AsyncLoggerContext.class, ctx,
                "this test must run with AsyncLoggerContextSelector (asyncLoggerTest task)");
        Configuration config = ctx.getConfiguration();

        Configurator.setLevel(LOGGER_NAME, org.apache.logging.log4j.Level.INFO);
        LoggerConfig loggerConfig = config.getLoggerConfig(LOGGER_NAME);
        CapturingAppender appender = new CapturingAppender();
        appender.start();
        loggerConfig.addAppender(appender, null, null);
        ctx.updateLoggers();

        try {
            Logger log = Logger.get(LOGGER_NAME).with()
                    .attr("component", "async-test")
                    .build();

            log.info()
                    .attr("msgId", "1:2")
                    .timed()
                    .log("hello async");
            log.info("plain message");

            awaitCaptures(appender, 2);

            CapturingAppender.Capture structured = appender.captures.get(0);
            assertEquals("hello async", structured.event().getMessage().getFormattedMessage());
            assertNotEquals(Thread.currentThread().getName(), structured.appendThread(),
                    "appender must run on the async background thread, not the caller");
            assertEquals(Thread.currentThread().getName(), structured.event().getThreadName(),
                    "event must be attributed to the producing thread");
            ReadOnlyStringMap data = structured.event().getContextData();
            assertEquals("async-test", data.getValue("component"));
            assertEquals("1:2", data.getValue("msgId"));
            assertNotNull(data.getValue("durationMs"));

            CapturingAppender.Capture plain = appender.captures.get(1);
            assertEquals("plain message", plain.event().getMessage().getFormattedMessage());
            assertNotEquals(Thread.currentThread().getName(), plain.appendThread());

            // The caller's ThreadContext must be restored after staging the attrs
            assertTrue(ThreadContext.isEmpty(), "caller ThreadContext must be left untouched");
        } finally {
            loggerConfig.removeAppender("AsyncCapture");
            appender.stop();
            config.removeLogger(LOGGER_NAME);
            ctx.updateLoggers();
        }
    }

    @Test
    void existingThreadContextIsPreservedAroundEmit() throws Exception {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();

        Configurator.setLevel(LOGGER_NAME, org.apache.logging.log4j.Level.INFO);
        LoggerConfig loggerConfig = config.getLoggerConfig(LOGGER_NAME);
        CapturingAppender appender = new CapturingAppender();
        appender.start();
        loggerConfig.addAppender(appender, null, null);
        ctx.updateLoggers();

        ThreadContext.put("requestId", "r-77");
        try {
            Logger log = Logger.get(LOGGER_NAME);
            log.info().attr("msgId", "9:9").log("with mdc");

            awaitCaptures(appender, 1);

            ReadOnlyStringMap data = appender.captures.get(0).event().getContextData();
            assertEquals("r-77", data.getValue("requestId"), "caller MDC must flow into the event");
            assertEquals("9:9", data.getValue("msgId"));

            assertEquals("r-77", ThreadContext.get("requestId"), "caller MDC must be restored");
            assertNull(ThreadContext.get("msgId"), "staged attrs must not leak into caller MDC");
        } finally {
            ThreadContext.clearMap();
            loggerConfig.removeAppender("AsyncCapture");
            appender.stop();
            config.removeLogger(LOGGER_NAME);
            ctx.updateLoggers();
        }
    }

    private static void awaitCaptures(CapturingAppender appender, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (appender.captures.size() < expected) {
            if (System.nanoTime() > deadline) {
                fail("timed out waiting for " + expected + " async log deliveries, got "
                        + appender.captures.size());
            }
            Thread.sleep(10);
        }
    }
}
