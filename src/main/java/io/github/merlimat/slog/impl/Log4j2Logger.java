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

import io.github.merlimat.slog.Logger;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.async.AsyncLogger;
import org.apache.logging.log4j.core.util.Constants;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.impl.ContextDataFactory;
import org.apache.logging.log4j.core.impl.MutableLogEvent;
import org.apache.logging.log4j.util.SortedArrayStringMap;
import org.apache.logging.log4j.util.StringMap;

/**
 * Logger implementation bound directly to a Log4j2 core Logger,
 * eliminating handler indirection on every call. Uses a thread-local
 * {@link MutableLogEvent} to avoid per-call allocation, and a
 * generation-counter scheme to cache the effective log level without
 * querying the Log4j2 hierarchy on every call.
 */
final class Log4j2Logger extends BaseLogger {
    /**
     * Bumped atomically by a single static listener whenever Log4j2's configuration
     * changes. The hot path reads it with {@code getOpaque()}, so no fence is paid.
     */
    private static final AtomicInteger GENERATION = new AtomicInteger();

    private static final VarHandle CACHED_GEN_AND_LEVEL;

    static {
        try {
            CACHED_GEN_AND_LEVEL = MethodHandles.lookup()
                    .findVarHandle(Log4j2Logger.class, "cachedGenAndLevel", long.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
        ((LoggerContext) LogManager.getContext(false))
                .addPropertyChangeListener(evt -> GENERATION.incrementAndGet());
    }

    /**
     * Mirrors log4j2's own event-factory selection ({@code LoggerConfig} uses
     * {@code ReusableLogEventFactory} only when thread-locals are enabled): events and
     * context maps are pooled in ThreadLocals only in that mode. With thread-locals
     * disabled (the default in web-app deployments), {@code AsyncLoggerConfig} enqueues
     * events <b>by reference</b>, so a reused event would be cleared and repopulated by
     * the application thread while the background thread still reads it — corrupting
     * log records. A fresh event per call keeps that configuration safe, exactly like
     * log4j2's own {@code DefaultLogEventFactory}.
     */
    private static final boolean POOL_EVENTS = Constants.ENABLE_THREADLOCALS;

    /**
     * Per-thread pooled emit state: the reusable event, its context map, and a flag
     * marking them in use. If user code logs through slog while an emit is already in
     * flight on the same thread — e.g. an attr supplier that logs, or a reentrant
     * appender — the nested emit must not clear the pooled instances mid-flight, so it
     * falls back to fresh allocations instead (the same protection log4j2's own
     * {@code ReusableLogEventFactory} gets from its {@code reserved} flag).
     */
    private static final class PooledEmitState {
        final MutableLogEvent event = new MutableLogEvent();
        final SortedArrayStringMap contextData = new SortedArrayStringMap();
        boolean inUse;
    }

    private static final ThreadLocal<PooledEmitState> POOLED_STATE =
            ThreadLocal.withInitial(PooledEmitState::new);

    /**
     * {@code Thread.isVirtual()}, resolved once — or constant {@code false} on JVMs
     * without virtual threads (this library compiles at Java 17, where the method does
     * not exist; the constant fallback lets the JIT erase the check entirely there).
     * Pooling is skipped on virtual threads: they are typically short-lived and can
     * exist in the millions, so per-thread pooled state would be allocated once, used
     * a handful of times, and abandoned — and the pool's memory footprint would scale
     * with the number of live virtual threads.
     */
    private static final MethodHandle IS_VIRTUAL = resolveIsVirtual();

    private static MethodHandle resolveIsVirtual() {
        try {
            return MethodHandles.publicLookup()
                    .findVirtual(Thread.class, "isVirtual", MethodType.methodType(boolean.class));
        } catch (ReflectiveOperationException e) {
            // Pre-Java 21: virtual threads do not exist
            return MethodHandles.dropArguments(
                    MethodHandles.constant(boolean.class, false), 0, Thread.class);
        }
    }

    static boolean isVirtual(Thread thread) {
        try {
            return (boolean) IS_VIRTUAL.invokeExact(thread);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Whether the LMAX Disruptor is on the classpath. {@link AsyncLogger} implements a
     * disruptor interface, so merely linking it (e.g. for an {@code instanceof} check)
     * throws {@link NoClassDefFoundError} when the optional disruptor jar is absent.
     * The {@code instanceof} below must only execute when this is true.
     */
    private static final boolean DISRUPTOR_PRESENT = isDisruptorPresent();

    private static boolean isDisruptorPresent() {
        try {
            Class.forName("com.lmax.disruptor.RingBuffer", false, Log4j2Logger.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    private final org.apache.logging.log4j.core.Logger log4j;

    /** True when the full-async selector is active and this logger enqueues to the disruptor. */
    private final boolean asyncLogger;

    /**
     * Cached (generation, effective intLevel) packed into a single long: generation in
     * the high 32 bits, intLevel in the low 32. Both halves are always written together,
     * so a concurrent reader can never observe a fresh generation paired with a stale
     * level. Accessed in opaque mode: atomic (no tearing) but fence-free, keeping the
     * disabled-path check as cheap as a plain field read.
     */
    private long cachedGenAndLevel = pack(-1, 0); // generation -1 forces the first refresh

    Log4j2Logger(String name, AttrChain contextAttrs, Clock clock) {
        super(name, contextAttrs, clock);
        this.log4j = (org.apache.logging.log4j.core.Logger) LogManager.getLogger(name);
        this.asyncLogger = DISRUPTOR_PRESENT && log4j instanceof AsyncLogger;
    }

    private Log4j2Logger(String name, org.apache.logging.log4j.core.Logger log4j,
                          AttrChain contextAttrs, Clock clock) {
        super(name, contextAttrs, clock);
        this.log4j = log4j;
        this.asyncLogger = DISRUPTOR_PRESENT && log4j instanceof AsyncLogger;
    }

    private int effectiveIntLevel() {
        int gen = GENERATION.getOpaque();
        long cached = (long) CACHED_GEN_AND_LEVEL.getOpaque(this);
        if ((int) (cached >>> 32) != gen) {
            int intLevel = log4j.getLevel().intLevel();
            CACHED_GEN_AND_LEVEL.setOpaque(this, pack(gen, intLevel));
            return intLevel;
        }
        return (int) cached;
    }

    private static long pack(int generation, int intLevel) {
        return ((long) generation << 32) | (intLevel & 0xFFFFFFFFL);
    }

    @Override
    protected boolean isTraceEnabled() {
        return org.apache.logging.log4j.Level.TRACE.intLevel() <= effectiveIntLevel();
    }

    @Override
    protected boolean isDebugEnabled() {
        return org.apache.logging.log4j.Level.DEBUG.intLevel() <= effectiveIntLevel();
    }

    @Override
    protected boolean isInfoEnabled() {
        return org.apache.logging.log4j.Level.INFO.intLevel() <= effectiveIntLevel();
    }

    @Override
    protected boolean isWarnEnabled() {
        return org.apache.logging.log4j.Level.WARN.intLevel() <= effectiveIntLevel();
    }

    @Override
    protected boolean isErrorEnabled() {
        return org.apache.logging.log4j.Level.ERROR.intLevel() <= effectiveIntLevel();
    }

    @Override
    protected void emit(String loggerName, Level level, String message,
                        AttrChain contextAttrs,
                        String[] eventKeys, Object[] eventValues, int eventAttrCount,
                        Throwable throwable, long durationNanos, String callerFqcn) {
        if (asyncLogger) {
            emitThroughAsyncLogger(level, message, contextAttrs, eventKeys, eventValues,
                    eventAttrCount, throwable, durationNanos, callerFqcn);
            return;
        }

        Thread currentThread = Thread.currentThread();
        PooledEmitState pooled = null;
        MutableLogEvent event;
        if (POOL_EVENTS && !isVirtual(currentThread)) {
            PooledEmitState state = POOLED_STATE.get();
            if (!state.inUse) {
                state.inUse = true;
                pooled = state;
                event = state.event;
                event.clear();
            } else {
                // Reentrant emit: the pooled instances belong to an in-flight emit
                // further up this thread's stack — use fresh ones.
                event = new MutableLogEvent();
            }
        } else {
            event = new MutableLogEvent();
        }

        try {
            event.setContextStack(org.apache.logging.log4j.ThreadContext.EMPTY_STACK);

            event.setLoggerName(loggerName);
            event.setLoggerFqcn(callerFqcn);
            event.setLevel(toLog4j2Level(level));
            event.setMessage(log4j.getMessageFactory().newMessage(message));
            event.setThrown(throwable);
            event.setContextData(buildContextData(pooled, contextAttrs, eventKeys, eventValues,
                    eventAttrCount, durationNanos));
            event.setTimeMillis(clock.millis());

            event.setThreadName(currentThread.getName());
            // event.setThreadId(currentThread.threadId()); // Only available in java >= 19
            event.setThreadPriority(currentThread.getPriority());

            LoggerConfig loggerConfig = log4j.get();
            loggerConfig.log(event);
        } finally {
            if (pooled != null) {
                pooled.inUse = false;
            }
        }
    }

    /**
     * With the full-async selector ({@code AsyncLoggerContextSelector}) the async hop
     * lives in {@link AsyncLogger#logMessage}: events are enqueued to the disruptor and
     * appenders run on a background thread. Calling {@code LoggerConfig.log(event)}
     * directly would skip that hop and run appender I/O on the application thread, so
     * this path goes through the logger's native API instead. Structured attrs are
     * staged in the {@link ThreadContext} for the duration of the call: the disruptor
     * translator captures context data on the producing thread before {@code logMessage}
     * returns, so the restore in {@code finally} cannot race with delivery.
     */
    private void emitThroughAsyncLogger(Level level, String message, AttrChain contextAttrs,
                                        String[] eventKeys, Object[] eventValues, int eventAttrCount,
                                        Throwable throwable, long durationNanos, String callerFqcn) {
        org.apache.logging.log4j.Level log4jLevel = toLog4j2Level(level);
        if (!hasContext(contextAttrs, eventAttrCount, durationNanos)) {
            log4j.logMessage(callerFqcn, log4jLevel, null,
                    log4j.getMessageFactory().newMessage(message), throwable);
            return;
        }

        // Batched on purpose: every ThreadContext.put() copy-on-writes the entire
        // backing array, so N direct puts would snapshot the context map N times.
        // Collecting into one map and calling putAll() snapshots it once.
        Map<String, String> attrs = new HashMap<>();
        for (int i = 0; i < contextAttrs.size(); i++) {
            Attr attr = contextAttrs.get(i);
            attrs.put(attr.key(), attr.valueAsString());
        }
        for (int i = 0; i < eventAttrCount; i++) {
            Object resolved = Attr.resolveValue(eventValues[i]);
            attrs.put(eventKeys[i], resolved == null ? null : String.valueOf(resolved));
        }
        if (durationNanos >= 0) {
            attrs.put("durationMs", String.valueOf(durationNanos / 1_000_000));
        }

        // Snapshot is safe: the underlying context map is copy-on-write, so later
        // puts cannot mutate the map view captured here.
        Map<String, String> saved = ThreadContext.getImmutableContext();
        try {
            ThreadContext.putAll(attrs);
            log4j.logMessage(callerFqcn, log4jLevel, null,
                    log4j.getMessageFactory().newMessage(message), throwable);
        } finally {
            ThreadContext.clearMap();
            ThreadContext.putAll(saved);
        }
    }

    private static StringMap buildContextData(PooledEmitState pooled, AttrChain contextAttrs,
                                              String[] eventKeys, Object[] eventValues,
                                              int eventAttrCount, long durationNanos) {
        // Fast path: empty MDC and no slog attrs — share a single frozen instance,
        // no allocation, no map clear.
        boolean mdcEmpty = ThreadContext.isEmpty();
        if (mdcEmpty && !hasContext(contextAttrs, eventAttrCount, durationNanos)) {
            return ContextDataFactory.emptyFrozenContextData();
        }

        StringMap map;
        if (pooled != null) {
            map = pooled.contextData;
            map.clear();
        } else {
            map = new SortedArrayStringMap();
        }
        if (!mdcEmpty) {
            // Inject log4j2 ThreadContext (MDC) so appenders / patterns that read
            // from event.getContextData() see the caller's MDC. getImmutableContext()
            // returns a backing-map reference (no copy), and forEach iterates without
            // allocating intermediate entries. Slog attrs added below override on
            // key collision.
            ThreadContext.getImmutableContext().forEach(map::putValue);
        }
        for (int i = 0; i < contextAttrs.size(); i++) {
            Attr attr = contextAttrs.get(i);
            map.putValue(attr.key(), attr.value());
        }
        for (int i = 0; i < eventAttrCount; i++) {
            map.putValue(eventKeys[i], Attr.resolveValue(eventValues[i]));
        }
        if (durationNanos >= 0) {
            map.putValue("durationMs", durationNanos / 1_000_000);
        }
        return map;
    }

    @Override
    public Logger derive(AttrChain contextAttrs) {
        return new Log4j2Logger(name(), log4j, contextAttrs, clock);
    }

    private static org.apache.logging.log4j.Level toLog4j2Level(Level level) {
        return switch (level) {
            case TRACE -> org.apache.logging.log4j.Level.TRACE;
            case DEBUG -> org.apache.logging.log4j.Level.DEBUG;
            case INFO -> org.apache.logging.log4j.Level.INFO;
            case WARN -> org.apache.logging.log4j.Level.WARN;
            case ERROR -> org.apache.logging.log4j.Level.ERROR;
        };
    }
}
