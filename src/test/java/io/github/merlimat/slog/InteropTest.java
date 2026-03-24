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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.OutputStreamAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.layout.JsonLayout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Tests that slog loggers coexist correctly with plain Log4j2 and SLF4J loggers,
 * particularly around MDC/ThreadContext preservation.
 */
class InteropTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String LOGGER_NAME = "io.github.merlimat.slog.InteropTest";

    @AfterEach
    void cleanup() {
        ThreadContext.clearMap();
    }

    /** Captures JSON output from the named Log4j2 logger. */
    private String captureJsonOutput(Runnable action) {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();

        JsonLayout layout = JsonLayout.newBuilder()
                .setConfiguration(config)
                .setCompact(true)
                .setEventEol(true)
                .setProperties(true)
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OutputStreamAppender appender = OutputStreamAppender.newBuilder()
                .setName("InteropCapture")
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
    void slogPreservesExistingThreadContext() {
        // Set up pre-existing ThreadContext (as another part of the app might)
        ThreadContext.put("requestId", "req-42");
        ThreadContext.put("traceId", "abc-123");

        // slog should not wipe these
        Logger slog = Logger.get(LOGGER_NAME);
        slog.info().attr("op", "test").log("slog message");

        // Pre-existing values must still be present
        assertEquals("req-42", ThreadContext.get("requestId"));
        assertEquals("abc-123", ThreadContext.get("traceId"));
        // slog attrs must NOT leak
        assertNull(ThreadContext.get("op"));
    }

    @Test
    void slogAttrsVisibleDuringLogCall() throws Exception {
        // Pre-existing context
        ThreadContext.put("requestId", "req-42");

        String json = captureJsonOutput(() -> {
            Logger slog = Logger.get(LOGGER_NAME);
            slog.info().attr("op", "test").log("slog message");
        });

        JsonNode node = MAPPER.readTree(json);
        JsonNode ctx = node.get("contextMap");
        // Both pre-existing and slog attrs should be present during the log call
        assertEquals("req-42", ctx.get("requestId").asText());
        assertEquals("test", ctx.get("op").asText());

        // After the call, pre-existing context is restored
        assertEquals("req-42", ThreadContext.get("requestId"));
        assertNull(ThreadContext.get("op"));
    }

    @Test
    void plainLog4j2LoggerAfterSlog() throws Exception {
        // Use slog first
        Logger slog = Logger.get(LOGGER_NAME);
        slog.info().attr("slogKey", "slogVal").log("from slog");

        // Then use plain Log4j2 — slog attrs should not leak
        String json = captureJsonOutput(() -> {
            org.apache.logging.log4j.Logger log4j = LogManager.getLogger(LOGGER_NAME);
            log4j.info("from log4j2");
        });

        JsonNode node = MAPPER.readTree(json);
        assertEquals("from log4j2", node.get("message").asText());
        // contextMap should be empty — no slog attrs leaked
        JsonNode ctx = node.get("contextMap");
        assertTrue(ctx == null || ctx.isEmpty(),
                "slog attrs should not leak to plain Log4j2 logger");
    }

    @Test
    void plainLog4j2LoggerBeforeSlog() throws Exception {
        // Use plain Log4j2 first with ThreadContext
        ThreadContext.put("existingKey", "existingVal");
        org.apache.logging.log4j.Logger log4j = LogManager.getLogger(LOGGER_NAME);
        log4j.info("from log4j2");

        // Then use slog — existing context preserved
        String json = captureJsonOutput(() -> {
            Logger slog = Logger.get(LOGGER_NAME);
            slog.info().attr("slogKey", "slogVal").log("from slog");
        });

        JsonNode node = MAPPER.readTree(json);
        JsonNode ctx = node.get("contextMap");
        assertEquals("existingVal", ctx.get("existingKey").asText());
        assertEquals("slogVal", ctx.get("slogKey").asText());

        // After slog call, original ThreadContext is restored
        assertEquals("existingVal", ThreadContext.get("existingKey"));
        assertNull(ThreadContext.get("slogKey"));
    }

    @Test
    void interleavedSlogAndLog4j2Calls() {
        ThreadContext.put("base", "value");

        Logger slog = Logger.get(LOGGER_NAME);

        // slog call
        slog.info().attr("a", 1).log("first");
        assertEquals("value", ThreadContext.get("base"));
        assertNull(ThreadContext.get("a"));

        // plain log4j2 call
        org.apache.logging.log4j.Logger log4j = LogManager.getLogger(LOGGER_NAME);
        log4j.info("plain");
        assertEquals("value", ThreadContext.get("base"));

        // another slog call
        slog.info().attr("b", 2).log("second");
        assertEquals("value", ThreadContext.get("base"));
        assertNull(ThreadContext.get("b"));
    }

    @Test
    void slogDoesNotLeakBetweenCalls() {
        Logger slog = Logger.get(LOGGER_NAME);

        slog.info().attr("first", "1").log("call 1");
        assertNull(ThreadContext.get("first"));

        slog.info().attr("second", "2").log("call 2");
        assertNull(ThreadContext.get("second"));
        assertNull(ThreadContext.get("first"));
    }

    @Test
    void slogWithContextAttrsDoesNotLeakToThreadContext() {
        Logger slog = Logger.get(LOGGER_NAME).with()
                .attr("component", "producer")
                .attr("topic", "orders")
                .build();

        slog.info("message");

        // Context attrs are on the logger, not the ThreadContext
        assertNull(ThreadContext.get("component"));
        assertNull(ThreadContext.get("topic"));
    }
}
