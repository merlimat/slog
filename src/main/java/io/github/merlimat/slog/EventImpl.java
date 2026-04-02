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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

class EventImpl implements Event {
    private static final String FQCN = EventImpl.class.getName();

    private final Logger logger;
    private final Level level;
    private final Clock clock;
    private List<Attr> attrs;
    private Throwable throwable;
    private Instant startTime;

    EventImpl(Logger logger, Level level, Clock clock) {
        this.logger = logger;
        this.level = level;
        this.clock = clock;
    }

    @Override
    public Event attr(String key, Object value) {
        if (attrs == null) {
            attrs = new ArrayList<>();
        }
        attrs.add(Attr.of(key, value));
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
    public Event attr(String key, Supplier<?> value) {
        return attr(key, (Object) value);
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
        this.startTime = clock.instant();
        return this;
    }

    @Override
    public void log(String msg) {
        emit(msg);
    }

    @Override
    public void log(Supplier<String> msgSupplier) {
        emit(msgSupplier.get());
    }

    @Override
    public void logf(String format, Object... args) {
        emit(String.format(format, args));
    }

    private void emit(String msg) {
        Duration duration = startTime != null
                ? Duration.between(startTime, clock.instant())
                : null;

        Iterable<Attr> merged = logger.mergeAttrs(attrs);
        LogRecord record = new LogRecord(
                logger.name(),
                level,
                msg,
                merged,
                throwable,
                clock.instant(),
                duration,
                FQCN
        );
        logger.handler().handle(record);
    }
}
