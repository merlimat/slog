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
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.time.Clock;
import java.time.Duration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;
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
    /** Bumped once by a single static listener whenever Log4j2's configuration changes. */
    private static int generation;

    private static final VarHandle GENERATION;

    static {
        try {
            GENERATION = MethodHandles.lookup()
                    .findStaticVarHandle(Log4j2Logger.class, "generation", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
        ((LoggerContext) LogManager.getContext(false))
                .addPropertyChangeListener(evt ->
                        GENERATION.setOpaque((int) GENERATION.getOpaque() + 1));
    }

    private static final ThreadLocal<MutableLogEvent> THREAD_LOCAL_EVENT =
            ThreadLocal.withInitial(MutableLogEvent::new);

    private static final ThreadLocal<StringMap> THREAD_LOCAL_CTX =
            ThreadLocal.withInitial(SortedArrayStringMap::new);

    private final org.apache.logging.log4j.core.Logger log4j;

    /** Cached effective intLevel — refreshed lazily when GENERATION changes. */
    private int cachedIntLevel;
    private int cachedGeneration = -1; // force first refresh

    Log4j2Logger(String name, AttrChain contextAttrs, Clock clock) {
        super(name, contextAttrs, clock);
        this.log4j = (org.apache.logging.log4j.core.Logger) LogManager.getLogger(name);
    }

    private Log4j2Logger(String name, org.apache.logging.log4j.core.Logger log4j,
                          AttrChain contextAttrs, Clock clock) {
        super(name, contextAttrs, clock);
        this.log4j = log4j;
    }

    private int effectiveIntLevel() {
        int gen = (int) GENERATION.getOpaque();
        if (gen != cachedGeneration) {
            cachedIntLevel = log4j.getLevel().intLevel();
            cachedGeneration = gen;
        }
        return cachedIntLevel;
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
                        Throwable throwable, Duration duration, String callerFqcn) {
        MutableLogEvent event = THREAD_LOCAL_EVENT.get();
        event.clear();
        event.setContextStack(org.apache.logging.log4j.ThreadContext.EMPTY_STACK);

        event.setLoggerName(loggerName);
        event.setLoggerFqcn(callerFqcn);
        event.setLevel(toLog4j2Level(level));
        event.setMessage(log4j.getMessageFactory().newMessage(message));
        event.setThrown(throwable);
        event.setContextData(buildContextData(contextAttrs, eventKeys, eventValues, eventAttrCount, duration));
        event.setTimeMillis(clock.millis());

        Thread currentThread = Thread.currentThread();
        event.setThreadName(currentThread.getName());
        // event.setThreadId(currentThread.threadId()); // Only available in java >= 19
        event.setThreadPriority(currentThread.getPriority());

        LoggerConfig loggerConfig = log4j.get();
        loggerConfig.log(event);
    }

    private static StringMap buildContextData(AttrChain contextAttrs,
                                              String[] eventKeys, Object[] eventValues,
                                              int eventAttrCount, Duration duration) {
        if (!hasContext(contextAttrs, eventAttrCount, duration)) {
            return null;
        }

        var map = THREAD_LOCAL_CTX.get();
        map.clear();
        for (Attr attr : contextAttrs) {
            map.putValue(attr.key(), attr.value());
        }
        for (int i = 0; i < eventAttrCount; i++) {
            map.putValue(eventKeys[i], Attr.resolveValue(eventValues[i]));
        }
        if (duration != null) {
            map.putValue("durationMs", duration.toMillis());
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
