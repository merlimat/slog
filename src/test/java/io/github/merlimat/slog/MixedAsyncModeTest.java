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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.async.AsyncLoggerConfig;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.Test;

/**
 * Verifies the mixed-async configuration ({@code <AsyncRoot>}, i.e.
 * {@code AsyncLoggerConfig}) with thread-locals disabled — the default in
 * web-app deployments. In that mode the disruptor enqueues events <b>by
 * reference</b>, so slog must not hand it a pooled, reused event: every
 * delivered record must be self-consistent and no record may be lost or
 * duplicated.
 *
 * <p>Run by the {@code mixedAsyncTest} Gradle task, which sets
 * {@code -Dlog4j2.enable.threadlocals=false} and the AsyncRoot configuration.
 */
class MixedAsyncModeTest {
    private static final String LOGGER_NAME = "io.github.merlimat.slog.MixedAsyncTest";

    private static final class CapturingAppender extends AbstractAppender {
        final List<LogEvent> events = new CopyOnWriteArrayList<>();

        CapturingAppender() {
            super("MixedAsyncCapture", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            events.add(event.toImmutable());
        }
    }

    @Test
    void mixedAsyncWithThreadLocalsDisabledDeliversConsistentEvents() throws Exception {
        assertFalse(org.apache.logging.log4j.core.util.Constants.ENABLE_THREADLOCALS,
                "this test must run with -Dlog4j2.enable.threadlocals=false (mixedAsyncTest task)");

        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        LoggerConfig root = config.getRootLogger();
        assertInstanceOf(AsyncLoggerConfig.class, root,
                "this test must run with the AsyncRoot configuration (mixedAsyncTest task)");

        CapturingAppender appender = new CapturingAppender();
        appender.start();
        root.addAppender(appender, null, null);
        ctx.updateLoggers();

        int n = 1000;
        try {
            Logger log = Logger.get(LOGGER_NAME);
            for (int i = 0; i < n; i++) {
                log.info().attr("i", i).log("msg-" + i);
            }
            awaitEvents(appender, n);
        } finally {
            root.removeAppender("MixedAsyncCapture");
            appender.stop();
            ctx.updateLoggers();
        }

        // Every record must be self-consistent (message matches its own attr) and
        // each message must be delivered exactly once. With a reused event in the
        // ring buffer, the application thread overwrites records the background
        // thread has not consumed yet, producing duplicates and mismatches.
        assertEquals(n, appender.events.size());
        Set<String> seen = new HashSet<>();
        for (LogEvent e : appender.events) {
            String msg = e.getMessage().getFormattedMessage();
            assertTrue(seen.add(msg), "duplicate message delivered: " + msg);
            Object i = e.getContextData().getValue("i");
            assertNotNull(i, "missing attr on: " + msg);
            assertEquals("msg-" + i, msg, "message and attr disagree — event was corrupted");
        }
        assertEquals(n, seen.size());
    }

    private static void awaitEvents(CapturingAppender appender, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (appender.events.size() < expected) {
            if (System.nanoTime() > deadline) {
                fail("timed out waiting for " + expected + " async log deliveries, got "
                        + appender.events.size());
            }
            Thread.sleep(10);
        }
    }
}
