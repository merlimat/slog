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
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.OutputStreamAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.layout.JsonLayout;
import org.junit.jupiter.api.Test;

/**
 * Integration test that exercises the real Log4j2 handler with JsonLayout,
 * capturing output and verifying the structured JSON.
 */
class Log4j2JsonOutputTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String LOGGER_NAME = "io.github.merlimat.slog.JsonTest";

    private String captureJsonOutput(Runnable logAction) {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();

        JsonLayout layout = JsonLayout.newBuilder()
                .setConfiguration(config)
                .setCompact(true)
                .setEventEol(true)
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OutputStreamAppender appender = OutputStreamAppender.newBuilder()
                .setName("TestCapture")
                .setLayout(layout)
                .setTarget(baos)
                .setConfiguration(config)
                .build();
        appender.start();

        // Attach directly to the target logger
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
    void jsonOutputContainsStructuredAttributes() throws Exception {
        String json = captureJsonOutput(() -> {
            Logger log = SLog.getLogger(LOGGER_NAME);
            log.info("Request handled", "method", "GET", "path", "/api/orders", "status", 200);
        });

        System.out.println("\n--- Log4j2 JSON output ---");
        System.out.println(json);
        System.out.println("---");

        assertFalse(json.isEmpty(), "Expected JSON output");
        JsonNode node = MAPPER.readTree(json);
        // StringMapMessage formats all key-value pairs (including msg) into the message field
        String message = node.get("message").asText();
        assertTrue(message.contains("method="));
        assertTrue(message.contains("GET"));
        assertTrue(message.contains("path="));
        assertTrue(message.contains("/api/orders"));
        assertTrue(message.contains("status="));
        assertTrue(message.contains("200"));
        assertTrue(message.contains("msg="));
        assertTrue(message.contains("Request handled"));
        assertEquals("INFO", node.get("level").asText());
        assertEquals(LOGGER_NAME, node.get("loggerName").asText());
    }

    @Test
    void jsonOutputWithContextAndException() throws Exception {
        String json = captureJsonOutput(() -> {
            Logger log = SLog.getLogger(LOGGER_NAME)
                    .with("topic", "persistent://public/default/orders")
                    .with("clientAddr", "10.0.0.1");

            log.error("Publish failed", new RuntimeException("Connection reset"),
                    "msgId", "1:2:3");
        });

        System.out.println("\n--- Log4j2 JSON output (context + exception) ---");
        System.out.println(json);
        System.out.println("---");

        assertFalse(json.isEmpty(), "Expected JSON output");
        JsonNode node = MAPPER.readTree(json);
        String message = node.get("message").asText();
        assertTrue(message.contains("topic="));
        assertTrue(message.contains("persistent://public/default/orders"));
        assertTrue(message.contains("clientAddr="));
        assertTrue(message.contains("10.0.0.1"));
        assertTrue(message.contains("msgId="));
        assertTrue(message.contains("1:2:3"));
        assertEquals("ERROR", node.get("level").asText());
        assertTrue(node.has("thrown"));
        assertEquals("Connection reset", node.get("thrown").get("message").asText());
    }

    @Test
    void jsonOutputWithFluentBuilder() throws Exception {
        String json = captureJsonOutput(() -> {
            Logger log = SLog.getLogger(LOGGER_NAME)
                    .with("namespace", "public/default");

            log.atInfo()
                    .attr("orderId", "ORD-12345")
                    .attr("items", 3)
                    .attr("total", 59.99)
                    .log("Order processed");
        });

        System.out.println("\n--- Log4j2 JSON output (fluent builder) ---");
        System.out.println(json);
        System.out.println("---");

        assertFalse(json.isEmpty(), "Expected JSON output");
        JsonNode node = MAPPER.readTree(json);
        String message = node.get("message").asText();
        assertTrue(message.contains("namespace="));
        assertTrue(message.contains("public/default"));
        assertTrue(message.contains("orderId="));
        assertTrue(message.contains("ORD-12345"));
        assertTrue(message.contains("items="));
        assertTrue(message.contains("3"));
    }
}
