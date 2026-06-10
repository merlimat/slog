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

import java.io.ByteArrayOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.OutputStreamAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the cached effective level in the Log4j2 handler is refreshed
 * when the configuration changes at runtime (the generation-counter scheme):
 * lowering and raising the level via {@link Configurator} must be picked up by
 * subsequent slog calls.
 */
class Log4j2DynamicLevelTest {
    private static final String LOGGER_NAME = "io.github.merlimat.slog.DynamicLevelTest";

    @Test
    void levelChangesAreVisibleAfterReconfiguration() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();

        // Dedicated LoggerConfig so the test can flip its level without touching root
        Configurator.setLevel(LOGGER_NAME, org.apache.logging.log4j.Level.INFO);
        LoggerConfig loggerConfig = config.getLoggerConfig(LOGGER_NAME);
        assertEquals(LOGGER_NAME, loggerConfig.getName());

        PatternLayout layout = PatternLayout.newBuilder()
                .withConfiguration(config)
                .withPattern("%level %message%n")
                .build();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OutputStreamAppender appender = OutputStreamAppender.newBuilder()
                .setName("DynamicLevelCapture")
                .setLayout(layout)
                .setTarget(baos)
                .setConfiguration(config)
                .build();
        appender.start();
        loggerConfig.addAppender(appender, null, null);
        ctx.updateLoggers();

        try {
            Logger log = Logger.get(LOGGER_NAME);

            log.debug("debug-at-info");   // suppressed, primes the level cache
            log.info("info-at-info");     // emitted

            Configurator.setLevel(LOGGER_NAME, org.apache.logging.log4j.Level.DEBUG);
            log.debug("debug-at-debug");  // cache must refresh: emitted

            Configurator.setLevel(LOGGER_NAME, org.apache.logging.log4j.Level.WARN);
            log.info("info-at-warn");     // cache must refresh again: suppressed
            log.warn("warn-at-warn");     // emitted
        } finally {
            loggerConfig.removeAppender("DynamicLevelCapture");
            appender.stop();
            config.removeLogger(LOGGER_NAME);
            ctx.updateLoggers();
        }

        String out = baos.toString();
        assertTrue(out.contains("info-at-info"), out);
        assertTrue(out.contains("debug-at-debug"), out);
        assertTrue(out.contains("warn-at-warn"), out);
        assertFalse(out.contains("debug-at-info"), out);
        assertFalse(out.contains("info-at-warn"), out);
    }
}
