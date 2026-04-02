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

import io.github.merlimat.slog.Event;
import io.github.merlimat.slog.Logger;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoggerTest {
    private List<LogRecord> records;
    private Set<Level> enabledLevels;

    @BeforeEach
    void setup() {
        records = new ArrayList<>();
        enabledLevels = Set.of(Level.INFO, Level.WARN, Level.ERROR);
    }

    /** Collects an Iterable<Attr> into a List for test assertions. */
    private static List<Attr> attrs(LogRecord r) {
        return StreamSupport.stream(r.attrs().spliterator(), false).toList();
    }

    @Test
    void simpleInfoLog() {
        Logger log = TestLogger.create("test", enabledLevels, records);
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
        Logger log = TestLogger.create("test", enabledLevels, records);
        log.info().attr("method", "GET").attr("path", "/api").log("request");

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
        Logger log = TestLogger.create("test", enabledLevels, records).with()
                .attr("topic", "persistent://t")
                .attr("clientAddr", "10.0.0.1")
                .build();

        log.info().attr("msgId", "1:2:3").log("published");

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
        Logger base = TestLogger.create("test", enabledLevels, records);
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
        Logger log = TestLogger.create("test", enabledLevels, records);
        log.debug("should be skipped");
        log.trace("should be skipped");

        assertEquals(0, records.size());
    }

    @Test
    void errorWithException() {
        Logger log = TestLogger.create("test", enabledLevels, records);
        RuntimeException ex = new RuntimeException("boom");
        log.error().attr("op", "write").exception(ex).log("failed");

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
        Logger log = TestLogger.create("test", enabledLevels, records);
        Event event = log.debug();

        // Should not throw or record anything
        event.attr("key", "val").timed().log("noop");
        assertEquals(0, records.size());
    }

    @Test
    void atInfoBuilderWorks() {
        Logger log = TestLogger.create("test", enabledLevels, records);
        log.info()
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

        Logger log = TestLogger.create("test", enabledLevels, records, advancingClock);
        Event e = log.info().timed();

        // Advance clock by 150ms
        clockRef[0] = Clock.fixed(start.plusMillis(150), ZoneOffset.UTC);
        e.log("slow op");

        assertEquals(1, records.size());
        LogRecord r = records.get(0);
        assertNotNull(r.duration());
        assertEquals(150, r.duration().toMillis());
    }

    @Test
    void allLevels() {
        enabledLevels = Set.of(Level.TRACE, Level.DEBUG, Level.INFO, Level.WARN, Level.ERROR);
        Logger log = TestLogger.create("test", enabledLevels, records);

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
        Logger log = TestLogger.create("test", enabledLevels, records)
                .with().attr("ctx", "value").build();

        log.info()
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
        Logger log = TestLogger.create("test", enabledLevels, records);
        log.info().attr("key", null).log("msg");

        assertEquals(1, records.size());
        assertNull(attrs(records.get(0)).get(0).value());
    }

    @Test
    void warnWithException() {
        Logger log = TestLogger.create("test", enabledLevels, records);
        Exception ex = new Exception("warning");
        log.warn().exception(ex).log("careful");

        assertEquals(1, records.size());
        LogRecord r = records.get(0);
        assertEquals(Level.WARN, r.level());
        assertSame(ex, r.throwable());
    }

    @Test
    void builderBatchesAttrsIntoSingleNode() {
        Logger log = TestLogger.create("test", enabledLevels, records).with()
                .attr("topic", "orders")
                .attr("clientAddr", "10.0.0.1")
                .attr("namespace", "public/default")
                .build();

        log.info().attr("msgId", "1:2:3").log("published");

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
        Logger base = TestLogger.create("test", enabledLevels, records);
        Logger same = base.with().build();
        assertSame(base, same);
    }

    @Test
    void deepChainPreservesParentFirstOrder() {
        Logger root = TestLogger.create("test", enabledLevels, records).with().attr("a", 1).build();
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
        Logger parent = TestLogger.create("test", enabledLevels, records).with().attr("shared", "val").build();

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
        Logger log1 = TestLogger.create("service1", enabledLevels, records).with()
                .attr("topic", "orders")
                .attr("clientAddr", "10.0.0.1")
                .build();

        Logger log2 = TestLogger.create("service2", enabledLevels, records).with()
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
        Logger empty = TestLogger.create("empty", enabledLevels, records);
        Logger log = TestLogger.create("test", enabledLevels, records).with()
                .ctx(empty)
                .attr("key", "val")
                .build();

        log.info("msg");

        assertEquals(1, records.size());
        List<Attr> a = attrs(records.get(0));
        assertEquals(1, a.size());
        assertEquals("key", a.get(0).key());
    }

    @Test
    void multipleCtxCallsAppendInOrder() {
        Logger producerLog = TestLogger.create("producer", enabledLevels, records).with()
                .attr("topic", "orders")
                .attr("clientAddr", "10.0.0.1")
                .build();

        Logger requestLog = TestLogger.create("request", enabledLevels, records).with()
                .attr("requestId", "req-42")
                .attr("traceId", "abc-123")
                .build();

        Logger log = TestLogger.create("test", enabledLevels, records).with()
                .ctx(producerLog)
                .ctx(requestLog)
                .attr("extra", "val")
                .build();

        log.info("combined");

        assertEquals(1, records.size());
        List<Attr> a = attrs(records.get(0));
        assertEquals(5, a.size());
        // producerLog attrs first (first ctx call)
        assertEquals("topic", a.get(0).key());
        assertEquals("clientAddr", a.get(1).key());
        // requestLog attrs next (second ctx call)
        assertEquals("requestId", a.get(2).key());
        assertEquals("traceId", a.get(3).key());
        // builder attrs last
        assertEquals("extra", a.get(4).key());
    }

    @Test
    void multipleCtxWithEmptyInBetween() {
        Logger log1 = TestLogger.create("s1", enabledLevels, records).with().attr("a", 1).build();
        Logger empty = TestLogger.create("empty", enabledLevels, records);
        Logger log2 = TestLogger.create("s2", enabledLevels, records).with().attr("b", 2).build();

        Logger log = TestLogger.create("test", enabledLevels, records).with()
                .ctx(log1)
                .ctx(empty)
                .ctx(log2)
                .build();

        log.info("msg");

        List<Attr> a = attrs(records.get(0));
        assertEquals(2, a.size());
        assertEquals("a", a.get(0).key());
        assertEquals("b", a.get(1).key());
    }

    @Test
    void duplicateKeysAcrossLevelsArePreserved() {
        // Duplicate keys at different levels are all kept — last-writer-wins
        // is left to the handler/backend, not the logger.
        Logger parent = TestLogger.create("test", enabledLevels, records).with()
                .attr("env", "staging")
                .build();

        Logger child = parent.with()
                .attr("env", "production")
                .attr("extra", "val")
                .build();

        child.info("msg");

        List<Attr> a = attrs(records.get(0));
        assertEquals(3, a.size());
        // Parent attr comes first
        assertEquals("env", a.get(0).key());
        assertEquals("staging", a.get(0).value());
        // Child attr with same key comes after
        assertEquals("env", a.get(1).key());
        assertEquals("production", a.get(1).value());
        assertEquals("extra", a.get(2).key());
    }

    @Test
    void duplicateKeysAcrossCtxAndBuilder() {
        Logger log1 = TestLogger.create("s1", enabledLevels, records).with()
                .attr("region", "us-east")
                .build();

        Logger log = TestLogger.create("test", enabledLevels, records).with()
                .ctx(log1)
                .attr("region", "eu-west")
                .build();

        log.info("msg");

        List<Attr> a = attrs(records.get(0));
        assertEquals(2, a.size());
        // ctx attrs first
        assertEquals("region", a.get(0).key());
        assertEquals("us-east", a.get(0).value());
        // builder attr with same key after
        assertEquals("region", a.get(1).key());
        assertEquals("eu-west", a.get(1).value());
    }

    @Test
    void duplicateKeysAcrossMultipleCtx() {
        Logger log1 = TestLogger.create("s1", enabledLevels, records).with()
                .attr("id", "from-log1")
                .build();
        Logger log2 = TestLogger.create("s2", enabledLevels, records).with()
                .attr("id", "from-log2")
                .build();

        Logger log = TestLogger.create("test", enabledLevels, records).with()
                .ctx(log1)
                .ctx(log2)
                .build();

        log.info("msg");

        List<Attr> a = attrs(records.get(0));
        assertEquals(2, a.size());
        assertEquals("id", a.get(0).key());
        assertEquals("from-log1", a.get(0).value());
        assertEquals("id", a.get(1).key());
        assertEquals("from-log2", a.get(1).value());
    }

    @Test
    void consumerVariantLogsWhenEnabled() {
        Logger log = TestLogger.create("test", enabledLevels, records);
        log.info(e -> e.attr("key", "val").log("lazy msg"));

        assertEquals(1, records.size());
        LogRecord r = records.get(0);
        assertEquals(Level.INFO, r.level());
        assertEquals("lazy msg", r.message());
        List<Attr> a = attrs(r);
        assertEquals(1, a.size());
        assertEquals("key", a.get(0).key());
        assertEquals("val", a.get(0).value());
    }

    @Test
    void consumerVariantSkipsLambdaWhenDisabled() {
        Logger log = TestLogger.create("test", enabledLevels, records);
        AtomicBoolean invoked = new AtomicBoolean(false);

        // DEBUG is disabled in this test setup
        log.debug(e -> {
            invoked.set(true);
            e.log("should not be called");
        });

        assertFalse(invoked.get());
        assertEquals(0, records.size());
    }

    @Test
    void consumerVariantIncludesContextAttrs() {
        Logger log = TestLogger.create("test", enabledLevels, records).with()
                .attr("ctx", "value")
                .build();

        log.info(e -> e.attr("event", "data").log("lazy"));

        assertEquals(1, records.size());
        List<Attr> a = attrs(records.get(0));
        assertEquals(2, a.size());
        assertEquals("ctx", a.get(0).key());
        assertEquals("event", a.get(1).key());
    }

    @Test
    void consumerVariantAllLevels() {
        enabledLevels = Set.of(Level.TRACE, Level.DEBUG, Level.INFO, Level.WARN, Level.ERROR);
        Logger log = TestLogger.create("test", enabledLevels, records);

        log.trace(e -> e.log("t"));
        log.debug(e -> e.log("d"));
        log.info(e -> e.log("i"));
        log.warn(e -> e.log("w"));
        log.error(e -> e.log("e"));

        assertEquals(5, records.size());
        assertEquals(Level.TRACE, records.get(0).level());
        assertEquals(Level.DEBUG, records.get(1).level());
        assertEquals(Level.INFO, records.get(2).level());
        assertEquals(Level.WARN, records.get(3).level());
        assertEquals(Level.ERROR, records.get(4).level());
    }

    @Test
    void consumerVariantWithException() {
        Logger log = TestLogger.create("test", enabledLevels, records);
        RuntimeException ex = new RuntimeException("boom");

        log.error(e -> e.attr("op", "write").exception(ex).log("failed"));

        assertEquals(1, records.size());
        LogRecord r = records.get(0);
        assertEquals("failed", r.message());
        assertSame(ex, r.throwable());
    }

    @Test
    void duplicateKeysInEventAttrs() {
        Logger log = TestLogger.create("test", enabledLevels, records).with()
                .attr("key", "from-context")
                .build();

        log.info().attr("key", "from-event").log("msg");

        List<Attr> a = attrs(records.get(0));
        assertEquals(2, a.size());
        assertEquals("key", a.get(0).key());
        assertEquals("from-context", a.get(0).value());
        assertEquals("key", a.get(1).key());
        assertEquals("from-event", a.get(1).value());
    }

    @Test
    void logfFormatsMessage() {
        Logger log = TestLogger.create("test", enabledLevels, records);
        log.info().attr("op", "resize").logf("Processed %d items in %dms", 42, 150);

        assertEquals(1, records.size());
        LogRecord r = records.get(0);
        assertEquals("Processed 42 items in 150ms", r.message());
        List<Attr> a = attrs(r);
        assertEquals(1, a.size());
        assertEquals("op", a.get(0).key());
    }

    @Test
    void logfWithStringFormatting() {
        Logger log = TestLogger.create("test", enabledLevels, records);
        log.info().logf("Hello %s, you have %d messages", "Alice", 5);

        assertEquals(1, records.size());
        assertEquals("Hello Alice, you have 5 messages", records.get(0).message());
    }

    @Test
    void logfOnDisabledLevelSkipsFormatting() {
        Logger log = TestLogger.create("test", enabledLevels, records);
        // DEBUG is disabled in this test setup
        log.debug().logf("Should not format %d", 42);

        assertEquals(0, records.size());
    }

    @Test
    void infofFormatsMessage() {
        Logger log = TestLogger.create("test", enabledLevels, records);
        log.infof("User %s has %d items", "Bob", 7);

        assertEquals(1, records.size());
        assertEquals("User Bob has 7 items", records.get(0).message());
        assertEquals(Level.INFO, records.get(0).level());
    }

    @Test
    void errorfFormatsMessage() {
        Logger log = TestLogger.create("test", enabledLevels, records);
        log.errorf("Failed after %d retries: %s", 3, "timeout");

        assertEquals(1, records.size());
        assertEquals("Failed after 3 retries: timeout", records.get(0).message());
        assertEquals(Level.ERROR, records.get(0).level());
    }

    @Test
    void warnfFormatsMessage() {
        Logger log = TestLogger.create("test", enabledLevels, records);
        log.warnf("Slow query: %.1fms", 123.4);

        assertEquals(1, records.size());
        assertEquals("Slow query: 123.4ms", records.get(0).message());
        assertEquals(Level.WARN, records.get(0).level());
    }

    @Test
    void debugfSkippedWhenDisabled() {
        Logger log = TestLogger.create("test", enabledLevels, records);
        log.debugf("Should not format %d", 99);

        assertEquals(0, records.size());
    }

    @Test
    void infofIncludesContextAttrs() {
        Logger log = TestLogger.create("test", enabledLevels, records).with()
                .attr("service", "orders")
                .build();
        log.infof("Processed %d items", 42);

        assertEquals(1, records.size());
        assertEquals("Processed 42 items", records.get(0).message());
        List<Attr> a = attrs(records.get(0));
        assertEquals(1, a.size());
        assertEquals("service", a.get(0).key());
    }

    @Test
    void exceptionMessageAttachesMessageAsAttr() {
        Logger log = TestLogger.create("test", enabledLevels, records);
        log.error().exceptionMessage(new RuntimeException("Connection reset")).log("failed");

        assertEquals(1, records.size());
        LogRecord r = records.get(0);
        assertNull(r.throwable());
        List<Attr> a = attrs(r);
        assertEquals(1, a.size());
        assertEquals("exception", a.get(0).key());
        assertEquals("Connection reset", a.get(0).value());
    }

    @Test
    void exceptionMessageWithNullMessageIsNoOp() {
        Logger log = TestLogger.create("test", enabledLevels, records);
        log.error().exceptionMessage(new RuntimeException((String) null)).log("failed");

        assertEquals(1, records.size());
        assertTrue(attrs(records.get(0)).isEmpty());
        assertNull(records.get(0).throwable());
    }

    @Test
    void exceptionMessageWithNullThrowableIsNoOp() {
        Logger log = TestLogger.create("test", enabledLevels, records);
        log.error().exceptionMessage(null).log("failed");

        assertEquals(1, records.size());
        assertTrue(attrs(records.get(0)).isEmpty());
    }

    @Test
    void attrLongOverload() {
        Logger log = TestLogger.create("test", enabledLevels, records);
        log.info().attr("ledgerId", 99L).attr("entryId", 42L).log("entry");

        assertEquals(1, records.size());
        List<Attr> a = attrs(records.get(0));
        assertEquals(2, a.size());
        assertEquals("ledgerId", a.get(0).key());
        assertEquals(99L, a.get(0).value());
        assertEquals("entryId", a.get(1).key());
        assertEquals(42L, a.get(1).value());
    }

    @Test
    void attrIntOverload() {
        Logger log = TestLogger.create("test", enabledLevels, records);
        log.info().attr("count", 7).attr("offset", 128).log("msg");

        assertEquals(1, records.size());
        List<Attr> a = attrs(records.get(0));
        assertEquals(2, a.size());
        assertEquals("count", a.get(0).key());
        assertEquals(7, a.get(0).value());
        assertEquals("offset", a.get(1).key());
        assertEquals(128, a.get(1).value());
    }

    @Test
    void attrDoubleOverload() {
        Logger log = TestLogger.create("test", enabledLevels, records);
        log.info().attr("latency", 3.14).log("msg");

        assertEquals(1, records.size());
        List<Attr> a = attrs(records.get(0));
        assertEquals(1, a.size());
        assertEquals("latency", a.get(0).key());
        assertEquals(3.14, a.get(0).value());
    }

    @Test
    void attrFloatOverload() {
        Logger log = TestLogger.create("test", enabledLevels, records);
        log.info().attr("ratio", 0.5f).log("msg");

        assertEquals(1, records.size());
        List<Attr> a = attrs(records.get(0));
        assertEquals(1, a.size());
        assertEquals("ratio", a.get(0).key());
        assertEquals(0.5f, a.get(0).value());
    }

    @Test
    void attrBooleanOverload() {
        Logger log = TestLogger.create("test", enabledLevels, records);
        log.info().attr("success", true).attr("retry", false).log("msg");

        assertEquals(1, records.size());
        List<Attr> a = attrs(records.get(0));
        assertEquals(2, a.size());
        assertEquals("success", a.get(0).key());
        assertEquals(true, a.get(0).value());
        assertEquals("retry", a.get(1).key());
        assertEquals(false, a.get(1).value());
    }

    @Test
    void attrPrimitiveMixedWithObject() {
        Logger log = TestLogger.create("test", enabledLevels, records);
        log.info()
                .attr("name", "test")
                .attr("ledgerId", 99L)
                .attr("count", 7)
                .attr("latency", 3.14)
                .attr("success", true)
                .log("mixed");

        assertEquals(1, records.size());
        List<Attr> a = attrs(records.get(0));
        assertEquals(5, a.size());
        assertEquals("test", a.get(0).value());
        assertEquals(99L, a.get(1).value());
        assertEquals(7, a.get(2).value());
        assertEquals(3.14, a.get(3).value());
        assertEquals(true, a.get(4).value());
    }

    @Test
    void attrPrimitiveOverloadsOnDisabledLevelAreNoop() {
        Logger log = TestLogger.create("test", enabledLevels, records);
        // DEBUG is disabled in this test setup
        Event event = log.debug();

        event.attr("a", 1L)
                .attr("b", 2)
                .attr("c", 3.0)
                .attr("d", 4.0f)
                .attr("e", true)
                .attr("f", "string")
                .log("noop");

        assertEquals(0, records.size());
    }

    @Test
    void supplierAttrResolvedAtEmitTime() {
        Logger log = TestLogger.create("test", enabledLevels, records);
        AtomicInteger counter = new AtomicInteger(0);
        Supplier<String> supplier = () -> "value-" + counter.incrementAndGet();

        log.info().attr("key", supplier).log("msg");

        assertEquals(1, records.size());
        List<Attr> a = attrs(records.get(0));
        assertEquals(1, a.size());
        assertEquals("key", a.get(0).key());
        assertEquals("value-1", a.get(0).valueAsString());
    }

    @Test
    void supplierAttrNotInvokedWhenDisabled() {
        Logger log = TestLogger.create("test", enabledLevels, records);
        AtomicBoolean invoked = new AtomicBoolean(false);
        Supplier<String> supplier = () -> {
            invoked.set(true);
            return "expensive";
        };

        // DEBUG is disabled in this test setup
        log.debug().attr("key", supplier).log("noop");

        assertFalse(invoked.get());
        assertEquals(0, records.size());
    }

    @Test
    void supplierMessageResolvedAtEmitTime() {
        Logger log = TestLogger.create("test", enabledLevels, records);
        AtomicInteger counter = new AtomicInteger(0);

        log.info().attr("op", "test").log(() -> "msg-" + counter.incrementAndGet());

        assertEquals(1, records.size());
        assertEquals("msg-1", records.get(0).message());
    }

    @Test
    void supplierMessageNotInvokedWhenDisabled() {
        Logger log = TestLogger.create("test", enabledLevels, records);
        AtomicBoolean invoked = new AtomicBoolean(false);

        log.debug().log(() -> {
            invoked.set(true);
            return "expensive";
        });

        assertFalse(invoked.get());
        assertEquals(0, records.size());
    }

    @Test
    void supplierContextAttrOnLogger() {
        AtomicInteger counter = new AtomicInteger(0);
        Supplier<String> supplier = () -> "state-" + counter.incrementAndGet();

        Logger log = TestLogger.create("test", enabledLevels, records).with()
                .attr("dynamic", supplier)
                .build();

        log.info("first");
        log.info("second");

        assertEquals(2, records.size());
        // Supplier is invoked each time
        assertEquals("state-1", attrs(records.get(0)).get(0).valueAsString());
        assertEquals("state-2", attrs(records.get(1)).get(0).valueAsString());
    }

    @Test
    void supplierContextAttrWithEventAttrs() {
        AtomicInteger counter = new AtomicInteger(0);
        Supplier<String> supplier = () -> "conn-" + counter.incrementAndGet();

        Logger log = TestLogger.create("test", enabledLevels, records).with()
                .attr("connState", supplier)
                .build();

        log.info().attr("op", "read").log("done");

        assertEquals(1, records.size());
        List<Attr> a = attrs(records.get(0));
        assertEquals(2, a.size());
        assertEquals("connState", a.get(0).key());
        assertEquals("conn-1", a.get(0).valueAsString());
        assertEquals("op", a.get(1).key());
        assertEquals("read", a.get(1).value());
    }

    @Test
    void supplierAttrReturningNonString() {
        Logger log = TestLogger.create("test", enabledLevels, records);
        AtomicInteger counter = new AtomicInteger(41);

        log.info().attr("count", (Supplier<?>) counter::incrementAndGet).log("msg");

        assertEquals(1, records.size());
        assertEquals("42", attrs(records.get(0)).get(0).valueAsString());
    }
}
