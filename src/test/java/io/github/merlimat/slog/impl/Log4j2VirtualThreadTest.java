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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

/**
 * Verifies the virtual-thread detection used to skip the per-thread event pools.
 * Sources compile at Java 17, so virtual threads are started reflectively; the
 * detection itself must report {@code false} on platform threads everywhere and
 * {@code true} on virtual threads when running on Java 21+.
 */
class Log4j2VirtualThreadTest {
    private static final String LOGGER_NAME = "io.github.merlimat.slog.impl.VirtualThreadTest";

    @Test
    void platformThreadIsNotVirtual() {
        assertFalse(Log4j2Logger.isVirtual(Thread.currentThread()));
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    void virtualThreadIsDetected() throws Exception {
        AtomicReference<Boolean> detected = new AtomicReference<>();
        Thread vt = startVirtualThread(() ->
                detected.set(Log4j2Logger.isVirtual(Thread.currentThread())));
        vt.join(10_000);
        assertEquals(Boolean.TRUE, detected.get());
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    void loggingFromVirtualThreadWorks() throws Exception {
        var appender = new CapturingAppender();
        appender.start();
        org.apache.logging.log4j.core.Logger log4jLogger =
                (org.apache.logging.log4j.core.Logger) LogManager.getLogger(LOGGER_NAME);
        log4jLogger.addAppender(appender);

        AtomicReference<Throwable> failure = new AtomicReference<>();
        try {
            Logger log = Logger.get(LOGGER_NAME).with().attr("component", "vt").build();
            Thread vt = startVirtualThread(() -> {
                try {
                    log.info().attr("msgId", "vt-1").log("from virtual thread");
                    log.info("plain from virtual thread");
                } catch (Throwable t) {
                    failure.set(t);
                }
            });
            vt.join(10_000);
            assertNull(failure.get());

            assertEquals(2, appender.events.size());
            LogEvent first = appender.events.get(0);
            assertEquals("from virtual thread", first.getMessage().getFormattedMessage());
            assertEquals("vt", first.getContextData().getValue("component"));
            assertEquals("vt-1", first.getContextData().getValue("msgId"));
            assertEquals("plain from virtual thread",
                    appender.events.get(1).getMessage().getFormattedMessage());
        } finally {
            log4jLogger.removeAppender(appender);
            appender.stop();
        }
    }

    private static Thread startVirtualThread(Runnable task) throws Exception {
        return (Thread) Thread.class.getMethod("startVirtualThread", Runnable.class)
                .invoke(null, task);
    }

    private static final class CapturingAppender extends AbstractAppender {
        final List<LogEvent> events = new CopyOnWriteArrayList<>();

        CapturingAppender() {
            super("VirtualThreadCapture", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            events.add(event.toImmutable());
        }
    }
}
