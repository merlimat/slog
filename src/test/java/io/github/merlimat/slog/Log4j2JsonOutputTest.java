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
 * capturing output and verifying that structured attributes appear as
 * individual fields in the JSON contextMap.
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
                .setProperties(true)
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OutputStreamAppender appender = OutputStreamAppender.newBuilder()
                .setName("TestCapture")
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
    void jsonOutputContainsStructuredAttributes() throws Exception {
        String json = captureJsonOutput(() -> {
            Logger log = Logger.get(LOGGER_NAME);
            log.info("Request handled", "method", "GET", "path", "/api/orders", "status", 200);
        });

        System.out.println("\n--- Log4j2 JSON output ---");
        System.out.println(json);
        System.out.println("---");

        assertFalse(json.isEmpty(), "Expected JSON output");
        JsonNode node = MAPPER.readTree(json);
        assertEquals("Request handled", node.get("message").asText());
        assertEquals("INFO", node.get("level").asText());
        assertEquals(LOGGER_NAME, node.get("loggerName").asText());

        // Attrs appear as individual fields in contextMap
        JsonNode ctx = node.get("contextMap");
        assertNotNull(ctx, "contextMap should be present");
        assertEquals("GET", ctx.get("method").asText());
        assertEquals("/api/orders", ctx.get("path").asText());
        assertEquals("200", ctx.get("status").asText());
    }

    @Test
    void jsonOutputWithContextAndException() throws Exception {
        String json = captureJsonOutput(() -> {
            Logger log = Logger.get(LOGGER_NAME).with()
                    .attr("topic", "persistent://public/default/orders")
                    .attr("clientAddr", "10.0.0.1")
                    .build();

            log.error("Publish failed", new RuntimeException("Connection reset"),
                    "msgId", "1:2:3");
        });

        System.out.println("\n--- Log4j2 JSON output (context + exception) ---");
        System.out.println(json);
        System.out.println("---");

        assertFalse(json.isEmpty(), "Expected JSON output");
        JsonNode node = MAPPER.readTree(json);
        assertEquals("Publish failed", node.get("message").asText());
        assertEquals("ERROR", node.get("level").asText());

        JsonNode ctx = node.get("contextMap");
        assertEquals("persistent://public/default/orders", ctx.get("topic").asText());
        assertEquals("10.0.0.1", ctx.get("clientAddr").asText());
        assertEquals("1:2:3", ctx.get("msgId").asText());

        assertTrue(node.has("thrown"));
        assertEquals("Connection reset", node.get("thrown").get("message").asText());
    }

    @Test
    void jsonOutputWithFluentBuilder() throws Exception {
        String json = captureJsonOutput(() -> {
            Logger log = Logger.get(LOGGER_NAME).with()
                    .attr("namespace", "public/default")
                    .build();

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
        assertEquals("Order processed", node.get("message").asText());

        JsonNode ctx = node.get("contextMap");
        assertEquals("public/default", ctx.get("namespace").asText());
        assertEquals("ORD-12345", ctx.get("orderId").asText());
        assertEquals("3", ctx.get("items").asText());
        assertEquals("59.99", ctx.get("total").asText());
    }
}
