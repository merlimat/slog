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

import java.time.Clock;
import java.util.Arrays;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Logger implementation bound directly to an SLF4J {@link org.slf4j.Logger},
 * eliminating handler indirection on every call.
 */
final class Slf4jLogger extends BaseLogger {
    private final org.slf4j.Logger slf4j;

    Slf4jLogger(String name, AttrChain contextAttrs, Clock clock) {
        super(name, contextAttrs, clock);
        this.slf4j = LoggerFactory.getLogger(name);
    }

    private Slf4jLogger(String name, org.slf4j.Logger slf4j, AttrChain contextAttrs, Clock clock) {
        super(name, contextAttrs, clock);
        this.slf4j = slf4j;
    }

    @Override
    protected boolean isTraceEnabled() { return slf4j.isTraceEnabled(); }

    @Override
    protected boolean isDebugEnabled() { return slf4j.isDebugEnabled(); }

    @Override
    protected boolean isInfoEnabled() { return slf4j.isInfoEnabled(); }

    @Override
    protected boolean isWarnEnabled() { return slf4j.isWarnEnabled(); }

    @Override
    protected boolean isErrorEnabled() { return slf4j.isErrorEnabled(); }

    @Override
    protected void emit(String loggerName, Level level, String message,
                        AttrChain contextAttrs,
                        Object[] eventAttrs, int eventAttrCount,
                        Throwable throwable, long durationNanos, String callerFqcn) {
        if (hasContext(contextAttrs, eventAttrCount, durationNanos)) {
            emitWithMdc(level, message, contextAttrs, eventAttrs, eventAttrCount,
                    throwable, durationNanos);
        } else {
            emitPlain(level, message, throwable);
        }
    }

    private void emitPlain(Level level, String msg, Throwable t) {
        switch (level) {
            case TRACE -> { if (t != null) slf4j.trace(msg, t); else slf4j.trace(msg); }
            case DEBUG -> { if (t != null) slf4j.debug(msg, t); else slf4j.debug(msg); }
            case INFO ->  { if (t != null) slf4j.info(msg, t);  else slf4j.info(msg);  }
            case WARN ->  { if (t != null) slf4j.warn(msg, t);  else slf4j.warn(msg);  }
            case ERROR -> { if (t != null) slf4j.error(msg, t); else slf4j.error(msg); }
        }
    }

    private void emitWithMdc(Level level, String msg, AttrChain contextAttrs,
                             Object[] eventAttrs, int eventAttrCount,
                             Throwable throwable, long durationNanos) {
        // Save and restore only the keys this event writes: copying the whole MDC
        // map (and restoring it with another full copy) scales with the ambient MDC
        // size — request ids, trace ids, etc. — which in real services dwarfs the
        // handful of keys a single event touches.
        MdcRestore mdc = new MdcRestore(eventAttrCount + 5);
        try {
            for (int i = 0; i < contextAttrs.size(); i++) {
                Attr attr = contextAttrs.get(i);
                mdc.put(attr.key(), attr.valueAsString());
            }
            for (int i = 0; i < eventAttrCount; i++) {
                Object resolved = Attr.resolveValue(eventAttrs[i * 2 + 1]);
                mdc.put((String) eventAttrs[i * 2], resolved == null ? null : String.valueOf(resolved));
            }
            if (durationNanos >= 0) {
                mdc.put("durationMs", String.valueOf(durationNanos / 1_000_000));
            }
            emitPlain(level, msg, throwable);
        } finally {
            mdc.restore();
        }
    }

    /**
     * Records the MDC keys written during one emit, along with their prior values,
     * so the restore touches only those keys. A key that was absent before the call
     * is removed on restore; an ambient value that was explicitly {@code null} is
     * restored as absent — the two are indistinguishable through {@code MDC.get}.
     */
    private static final class MdcRestore {
        private String[] keys;
        private String[] prior;
        private int count;

        MdcRestore(int initialCapacity) {
            keys = new String[initialCapacity];
            prior = new String[initialCapacity];
        }

        void put(String key, String value) {
            if (count == keys.length) {
                keys = Arrays.copyOf(keys, count * 2);
                prior = Arrays.copyOf(prior, count * 2);
            }
            keys[count] = key;
            prior[count] = MDC.get(key);
            count++;
            MDC.put(key, value);
        }

        void restore() {
            // Reverse order, so with duplicate keys the first-seen prior value wins
            for (int i = count - 1; i >= 0; i--) {
                if (prior[i] != null) {
                    MDC.put(keys[i], prior[i]);
                } else {
                    MDC.remove(keys[i]);
                }
            }
        }
    }

    @Override
    public Logger derive(AttrChain contextAttrs) {
        return new Slf4jLogger(name(), slf4j, contextAttrs, clock);
    }
}
