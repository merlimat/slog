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
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.Test;

/**
 * Verifies that Log4j2 can resolve the correct caller file name and line
 * number when logging through slog, not slog's internal classes.
 */
class Log4j2CallerInfoTest {
    private static final String LOGGER_NAME = "io.github.merlimat.slog.CallerInfoTest";

    private String captureWithPattern(String pattern, Runnable action) {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();

        PatternLayout layout = PatternLayout.newBuilder()
                .withConfiguration(config)
                .withPattern(pattern)
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OutputStreamAppender appender = OutputStreamAppender.newBuilder()
                .setName("CallerCapture")
                .setLayout(layout)
                .setTarget(baos)
                .setConfiguration(config)
                .build();
        appender.start();

        org.apache.logging.log4j.core.Logger logger =
                (org.apache.logging.log4j.core.Logger) LogManager.getLogger(LOGGER_NAME);
        logger.addAppender(appender);

        try {
            action.run();
        } finally {
            logger.removeAppender(appender);
            appender.stop();
        }

        return baos.toString().trim();
    }

    @Test
    void callerFileAndLineAreResolved() {
        // %F = file name, %L = line number, %M = method name
        String output = captureWithPattern("%F:%L %M - %message%n", () -> {
            Logger log = Logger.get(LOGGER_NAME);
            log.info("caller info test"); // this is the call site
        });

        System.out.println("\n--- Log4j2 caller info ---");
        System.out.println(output);
        System.out.println("---");

        // Should reference this test file, not Logger.java or Log4j2Handler.java
        assertTrue(output.contains("Log4j2CallerInfoTest.java"),
                "Expected caller file to be this test class, got: " + output);
        assertTrue(output.contains("callerFileAndLineAreResolved"),
                "Expected caller method name, got: " + output);
    }

    @Test
    void callerInfoWithFluentBuilder() {
        String output = captureWithPattern("%F:%L - %message %X%n", () -> {
            Logger log = Logger.get(LOGGER_NAME);
            log.info().attr("key", "val").log("fluent test"); // this is the call site
        });

        System.out.println("\n--- Log4j2 caller info (fluent) ---");
        System.out.println(output);
        System.out.println("---");

        assertTrue(output.contains("Log4j2CallerInfoTest.java"),
                "Expected caller file to be this test class, got: " + output);
    }
}
