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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoggerTest {
    private List<LogRecord> records;
    private Set<Level> enabledLevels;
    private Handler handler;

    @BeforeEach
    void setup() {
        records = new ArrayList<>();
        enabledLevels = Set.of(Level.INFO, Level.WARN, Level.ERROR);
        handler = new Handler() {
            @Override
            public boolean isEnabled(String loggerName, Level level) {
                return enabledLevels.contains(level);
            }

            @Override
            public void handle(LogRecord record) {
                records.add(record);
            }
        };
    }

    /** Collects an Iterable<Attr> into a List for test assertions. */
    private static List<Attr> attrs(LogRecord r) {
        return StreamSupport.stream(r.attrs().spliterator(), false).toList();
    }

    @Test
    void simpleInfoLog() {
        Logger log = Logger.get("test", handler);
        log.info("hello");

        assertEquals(1, records.size());
        LogRecord r = records.get(0);
        assertEquals("test", r.loggerName());
        assertEquals(Level.INFO, r.level());
        assertEquals("hello", r.message());
        assertTrue(attrs(r).isEmpty());
        assertNull(r.throwable());
        assertNull(r.duration());
    }

    @Test
    void infoWithKeyValuePairs() {
        Logger log = Logger.get("test", handler);
        log.info("request", "method", "GET", "path", "/api");

        assertEquals(1, records.size());
        LogRecord r = records.get(0);
        assertEquals("request", r.message());
        List<Attr> a = attrs(r);
        assertEquals(2, a.size());
        assertEquals("method", a.get(0).key());
        assertEquals("GET", a.get(0).value());
        assertEquals("path", a.get(1).key());
        assertEquals("/api", a.get(1).value());
    }

    @Test
    void contextPropagationWithWith() {
        Logger log = Logger.get("test", handler).with()
                .attr("topic", "persistent://t")
                .attr("clientAddr", "10.0.0.1")
                .build();

        log.info("published", "msgId", "1:2:3");

        assertEquals(1, records.size());
        LogRecord r = records.get(0);
        List<Attr> a = attrs(r);
        assertEquals(3, a.size());
        assertEquals("topic", a.get(0).key());
        assertEquals("persistent://t", a.get(0).value());
        assertEquals("clientAddr", a.get(1).key());
        assertEquals("10.0.0.1", a.get(1).value());
        assertEquals("msgId", a.get(2).key());
        assertEquals("1:2:3", a.get(2).value());
    }

    @Test
    void withCreatesNewLogger() {
        Logger base = Logger.get("test", handler);
        Logger derived = base.with().attr("key", "val").build();

        assertNotSame(base, derived);

        base.info("from base");
        derived.info("from derived");

        assertEquals(2, records.size());
        assertTrue(attrs(records.get(0)).isEmpty());
        assertEquals(1, attrs(records.get(1)).size());
    }

    @Test
    void disabledLevelDoesNotInvokeHandler() {
        Logger log = Logger.get("test", handler);
        log.debug("should be skipped");
        log.trace("should be skipped");

        assertEquals(0, records.size());
    }

    @Test
    void errorWithException() {
        Logger log = Logger.get("test", handler);
        RuntimeException ex = new RuntimeException("boom");
        log.error("failed", ex, "op", "write");

        assertEquals(1, records.size());
        LogRecord r = records.get(0);
        assertEquals(Level.ERROR, r.level());
        assertEquals("failed", r.message());
        assertSame(ex, r.throwable());
        List<Attr> a = attrs(r);
        assertEquals(1, a.size());
        assertEquals("op", a.get(0).key());
    }

    @Test
    void atInfoReturnsNoopWhenDisabled() {
        Logger log = Logger.get("test", handler);
        Event event = log.atDebug();

        assertSame(NoopEvent.INSTANCE, event);

        // Should not throw or record anything
        event.attr("key", "val").timed().log("noop");
        assertEquals(0, records.size());
    }

    @Test
    void atInfoBuilderWorks() {
        Logger log = Logger.get("test", handler);
        log.atInfo()
                .attr("key", "val")
                .exception(new RuntimeException("err"))
                .log("event msg");

        assertEquals(1, records.size());
        LogRecord r = records.get(0);
        assertEquals("event msg", r.message());
        List<Attr> a = attrs(r);
        assertEquals(1, a.size());
        assertEquals("key", a.get(0).key());
        assertNotNull(r.throwable());
    }

    @Test
    void timedEvent() {
        Instant start = Instant.parse("2024-01-01T00:00:00Z");
        Clock fixedClock = Clock.fixed(start, ZoneOffset.UTC);
        // Use a clock that advances
        Clock[] clockRef = { fixedClock };
        Clock advancingClock = new Clock() {
            @Override
            public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            @Override
            public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override
            public Instant instant() { return clockRef[0].instant(); }
        };

        Logger log = Logger.get("test", handler, advancingClock);
        Event e = log.atInfo().timed();

        // Advance clock by 150ms
        clockRef[0] = Clock.fixed(start.plusMillis(150), ZoneOffset.UTC);
        e.log("slow op");

        assertEquals(1, records.size());
        LogRecord r = records.get(0);
        assertNotNull(r.duration());
        assertEquals(150, r.duration().toMillis());
    }

    @Test
    void isEnabledCheck() {
        Logger log = Logger.get("test", handler);
        assertTrue(log.isEnabled(Level.INFO));
        assertTrue(log.isEnabled(Level.WARN));
        assertTrue(log.isEnabled(Level.ERROR));
        assertFalse(log.isEnabled(Level.DEBUG));
        assertFalse(log.isEnabled(Level.TRACE));
    }

    @Test
    void allLevels() {
        enabledLevels = Set.of(Level.TRACE, Level.DEBUG, Level.INFO, Level.WARN, Level.ERROR);
        Logger log = Logger.get("test", handler);

        log.trace("t");
        log.debug("d");
        log.info("i");
        log.warn("w");
        log.error("e");

        assertEquals(5, records.size());
        assertEquals(Level.TRACE, records.get(0).level());
        assertEquals(Level.DEBUG, records.get(1).level());
        assertEquals(Level.INFO, records.get(2).level());
        assertEquals(Level.WARN, records.get(3).level());
        assertEquals(Level.ERROR, records.get(4).level());
    }

    @Test
    void contextAttrsWithEventBuilder() {
        Logger log = Logger.get("test", handler)
                .with().attr("ctx", "value").build();

        log.atInfo()
                .attr("event", "data")
                .log("test");

        assertEquals(1, records.size());
        LogRecord r = records.get(0);
        List<Attr> a = attrs(r);
        assertEquals(2, a.size());
        assertEquals("ctx", a.get(0).key());
        assertEquals("event", a.get(1).key());
    }

    @Test
    void nullValueHandling() {
        Logger log = Logger.get("test", handler);
        log.info("msg", "key", null);

        assertEquals(1, records.size());
        assertNull(attrs(records.get(0)).get(0).value());
    }

    @Test
    void warnWithException() {
        Logger log = Logger.get("test", handler);
        Exception ex = new Exception("warning");
        log.warn("careful", ex);

        assertEquals(1, records.size());
        LogRecord r = records.get(0);
        assertEquals(Level.WARN, r.level());
        assertSame(ex, r.throwable());
    }

    @Test
    void builderBatchesAttrsIntoSingleNode() {
        Logger log = Logger.get("test", handler).with()
                .attr("topic", "orders")
                .attr("clientAddr", "10.0.0.1")
                .attr("namespace", "public/default")
                .build();

        log.info("published", "msgId", "1:2:3");

        assertEquals(1, records.size());
        List<Attr> a = attrs(records.get(0));
        assertEquals(4, a.size());
        assertEquals("topic", a.get(0).key());
        assertEquals("orders", a.get(0).value());
        assertEquals("clientAddr", a.get(1).key());
        assertEquals("namespace", a.get(2).key());
        assertEquals("msgId", a.get(3).key());
    }

    @Test
    void builderWithNoAttrsReturnsSameLogger() {
        Logger base = Logger.get("test", handler);
        Logger same = base.with().build();
        assertSame(base, same);
    }

    @Test
    void deepChainPreservesParentFirstOrder() {
        Logger root = Logger.get("test", handler).with().attr("a", 1).build();
        Logger child = root.with().attr("b", 2).build();
        Logger grandchild = child.with().attr("c", 3).build();

        grandchild.info("deep");

        assertEquals(1, records.size());
        List<Attr> a = attrs(records.get(0));
        assertEquals(3, a.size());
        assertEquals("a", a.get(0).key());
        assertEquals("b", a.get(1).key());
        assertEquals("c", a.get(2).key());
    }

    @Test
    void siblingsShareParentAttrs() {
        Logger parent = Logger.get("test", handler).with().attr("shared", "val").build();

        Logger child1 = parent.with().attr("child", "1").build();
        Logger child2 = parent.with().attr("child", "2").build();

        child1.info("from child1");
        child2.info("from child2");

        assertEquals(2, records.size());
        List<Attr> a1 = attrs(records.get(0));
        List<Attr> a2 = attrs(records.get(1));
        assertEquals("shared", a1.get(0).key());
        assertEquals("shared", a2.get(0).key());
        assertEquals("1", a1.get(1).value());
        assertEquals("2", a2.get(1).value());
    }

    @Test
    void ctxPropagatesContextAcrossLoggers() {
        Logger log1 = Logger.get("service1", handler).with()
                .attr("topic", "orders")
                .attr("clientAddr", "10.0.0.1")
                .build();

        Logger log2 = Logger.get("service2", handler).with()
                .ctx(log1)
                .attr("msgId", "1:2:3")
                .build();

        log2.info("processed");

        assertEquals(1, records.size());
        List<Attr> a = attrs(records.get(0));
        assertEquals(3, a.size());
        // Inherited attrs come first
        assertEquals("topic", a.get(0).key());
        assertEquals("orders", a.get(0).value());
        assertEquals("clientAddr", a.get(1).key());
        assertEquals("10.0.0.1", a.get(1).value());
        // Then the builder's own attrs
        assertEquals("msgId", a.get(2).key());
        assertEquals("1:2:3", a.get(2).value());
        // Logger name is from the new logger, not the inherited one
        assertEquals("service2", records.get(0).loggerName());
    }

    @Test
    void ctxWithEmptyLoggerIsNoOp() {
        Logger empty = Logger.get("empty", handler);
        Logger log = Logger.get("test", handler).with()
                .ctx(empty)
                .attr("key", "val")
                .build();

        log.info("msg");

        assertEquals(1, records.size());
        List<Attr> a = attrs(records.get(0));
        assertEquals(1, a.size());
        assertEquals("key", a.get(0).key());
    }
}
