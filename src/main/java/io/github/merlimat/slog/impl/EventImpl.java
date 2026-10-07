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

import io.github.merlimat.slog.Event;
import io.github.merlimat.slog.Logger;
import io.github.merlimat.slog.ThrowingSupplier;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;

final class EventImpl implements Event {
    static final String FQCN = EventImpl.class.getName();

    private static final int INITIAL_CAPACITY = 8; // 4 pairs

    private final BaseLogger logger;
    private final Level level;
    private final Clock clock;
    /** Per-event attributes, interleaved as {@code {key, value, key, value, ...}}. */
    private Object[] attrs;
    private int attrCount; // number of key-value pairs
    private Throwable throwable;
    private boolean timed;
    private long startNanos;
    private AttrChain extraContext = AttrChain.EMPTY;

    EventImpl(BaseLogger logger, Level level, Clock clock) {
        this.logger = logger;
        this.level = level;
        this.clock = clock;
    }

    @Override
    public Event attr(String key, Object value) {
        int slot = attrCount * 2;
        if (attrs == null) {
            attrs = new Object[INITIAL_CAPACITY];
        } else if (slot == attrs.length) {
            attrs = Arrays.copyOf(attrs, slot * 2);
        }
        attrs[slot] = key;
        attrs[slot + 1] = value;
        attrCount++;
        return this;
    }

    @Override
    public Event attr(String key, long value) {
        return attr(key, (Object) value);
    }

    @Override
    public Event attr(String key, int value) {
        return attr(key, (Object) value);
    }

    @Override
    public Event attr(String key, double value) {
        return attr(key, (Object) value);
    }

    @Override
    public Event attr(String key, float value) {
        return attr(key, (Object) value);
    }

    @Override
    public Event attr(String key, boolean value) {
        return attr(key, (Object) value);
    }

    @Override
    public Event attr(String key, ThrowingSupplier<?> value) {
        return attr(key, (Object) value);
    }

    @Override
    public Event ctx(Logger other) {
        if (other == null) {
            return this;
        }
        AttrChain otherCtx = ((BaseLogger) other).contextAttrs();
        if (!otherCtx.isEmpty()) {
            extraContext = otherCtx.withPrefix(extraContext);
        }
        return this;
    }

    @Override
    public Event exception(Throwable t) {
        if (t != null) {
            this.throwable = t;
        }
        return this;
    }

    @Override
    public Event exceptionMessage(Throwable t) {
        if (t != null && t.getMessage() != null) {
            return attr("exception", t.getMessage());
        }
        return this;
    }

    @Override
    public Event timed() {
        this.timed = true;
        this.startNanos = logger.nanoTime();
        return this;
    }

    @Override
    public Event onceEvery(int n) {
        if (n <= 1) {
            return this;
        }
        long skipped = RateLimiter.checkEvery(n);
        if (skipped != RateLimiter.SUPPRESSED) {
            if (skipped > 0) {
                attr("skipped", skipped);
            }
            return this;
        }
        return NoopEvent.INSTANCE;
    }

    @Override
    public Event onceEvery(Duration duration) {
        long skipped = RateLimiter.checkAtMostEvery(duration.toMillis(), clock.millis());
        if (skipped != RateLimiter.SUPPRESSED) {
            if (skipped > 0) {
                attr("skipped", skipped);
            }
            return this;
        }
        return NoopEvent.INSTANCE;
    }

    @Override
    public void log(String msg) {
        emit(msg);
    }

    @Override
    public void log(ThrowingSupplier<String> msgSupplier) {
        try {
            emit(msgSupplier.get());
        } catch (Exception e) {
            emit("<error: " + e.getMessage() + ">");
        }
    }

    @Override
    public void logf(String format, Object... args) {
        emit(String.format(format, args));
    }

    private void emit(String msg) {
        long durationNanos = timed
                ? Math.max(0, logger.nanoTime() - startNanos)
                : -1;

        AttrChain contextAttrs = extraContext.isEmpty()
                ? logger.contextAttrs()
                : logger.contextAttrs().withPrefix(extraContext);

        logger.emit(logger.name(), level, msg,
                contextAttrs,
                attrs, attrCount,
                throwable, durationNanos, FQCN);
    }
}
