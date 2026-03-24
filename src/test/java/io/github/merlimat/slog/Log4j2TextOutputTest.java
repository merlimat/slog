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
 * Integration test that exercises the Log4j2 handler with a PatternLayout,
 * showing how structured attributes appear in traditional text log output
 * via the ThreadContext (MDC).
 */
class Log4j2TextOutputTest {
    private static final String LOGGER_NAME = "io.github.merlimat.slog.TextTest";

    private String captureTextOutput(String pattern, Runnable logAction) {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();

        PatternLayout layout = PatternLayout.newBuilder()
                .withConfiguration(config)
                .withPattern(pattern)
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OutputStreamAppender appender = OutputStreamAppender.newBuilder()
                .setName("TextCapture")
                .setLayout(layout)
                .setTarget(baos)
                .setConfiguration(config)
                .build();
        appender.start();

        org.apache.logging.log4j.core.Logger logger =
                (org.apache.logging.log4j.core.Logger) LogManager.getLogger(LOGGER_NAME);
        logger.addAppender(appender);

        try {
            logAction.run();
        } finally {
            logger.removeAppender(appender);
            appender.stop();
        }

        return baos.toString().trim();
    }

    @Test
    void textOutputWithContextMap() {
        String output = captureTextOutput(
                "%d{HH:mm:ss.SSS} %-5level [%logger{36}] %message %X%n",
                () -> {
                    Logger log = SLog.getLogger(LOGGER_NAME)
                            .with("topic", "orders")
                            .with("clientAddr", "10.0.0.1");
                    log.info("Message published", "msgId", "1:2:3");
                });

        System.out.println("\n--- Log4j2 text output (%X = full context map) ---");
        System.out.println(output);
        System.out.println("---");

        assertTrue(output.contains("INFO"));
        assertTrue(output.contains("Message published"));
        assertTrue(output.contains("topic=orders"));
        assertTrue(output.contains("clientAddr=10.0.0.1"));
        assertTrue(output.contains("msgId=1:2:3"));
    }

    @Test
    void textOutputWithIndividualKeys() {
        String output = captureTextOutput(
                "%-5level %message [topic=%X{topic} client=%X{clientAddr}]%n",
                () -> {
                    Logger log = SLog.getLogger(LOGGER_NAME)
                            .with("topic", "orders")
                            .with("clientAddr", "10.0.0.1");
                    log.info("Published");
                });

        System.out.println("\n--- Log4j2 text output (%X{key} = individual keys) ---");
        System.out.println(output);
        System.out.println("---");

        assertTrue(output.contains("INFO"));
        assertTrue(output.contains("Published"));
        assertTrue(output.contains("topic=orders"));
        assertTrue(output.contains("client=10.0.0.1"));
    }

    @Test
    void textOutputWithException() {
        String output = captureTextOutput(
                "%-5level %message %X %ex{short}%n",
                () -> {
                    Logger log = SLog.getLogger(LOGGER_NAME)
                            .with("op", "publish");
                    log.error("Failed", new RuntimeException("Connection reset"));
                });

        System.out.println("\n--- Log4j2 text output (with exception) ---");
        System.out.println(output);
        System.out.println("---");

        assertTrue(output.contains("ERROR"));
        assertTrue(output.contains("Failed"));
        assertTrue(output.contains("op=publish"));
        assertTrue(output.contains("Connection reset"));
    }
}
