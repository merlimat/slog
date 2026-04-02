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

import java.util.function.Supplier;

enum NoopEvent implements Event {
    INSTANCE;

    @Override
    public Event attr(String key, Object value) {
        return this;
    }

    @Override
    public Event attr(String key, long value) {
        return this;
    }

    @Override
    public Event attr(String key, int value) {
        return this;
    }

    @Override
    public Event attr(String key, double value) {
        return this;
    }

    @Override
    public Event attr(String key, float value) {
        return this;
    }

    @Override
    public Event attr(String key, boolean value) {
        return this;
    }

    @Override
    public Event attr(String key, Supplier<?> value) {
        return this;
    }

    @Override
    public Event exception(Throwable t) {
        return this;
    }

    @Override
    public Event exceptionMessage(Throwable t) {
        return this;
    }

    @Override
    public Event timed() {
        return this;
    }

    @Override
    public void log(String msg) {
        // no-op
    }

    @Override
    public void log(Supplier<String> msgSupplier) {
        // no-op — supplier is never invoked
    }

    @Override
    public void logf(String format, Object... args) {
        // no-op — formatting is skipped entirely
    }
}
