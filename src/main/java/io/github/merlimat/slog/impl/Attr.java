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

import io.github.merlimat.slog.ThrowingSupplier;

/**
 * A key-value pair representing a single structured logging attribute.
 *
 * <p>Attrs are the fundamental unit of structured data in slog. They are attached
 * to log events either as context on a {@link io.github.merlimat.slog.Logger}
 * (via {@link io.github.merlimat.slog.Logger#with}) or per-event
 * (via {@link io.github.merlimat.slog.Event#attr}).
 *
 * @param key   the attribute name
 * @param value the attribute value, may be {@code null}
 */
record Attr(String key, Object value) {

    /**
     * Creates a new attribute with the given key and value.
     *
     * @param key   the attribute name
     * @param value the attribute value, may be {@code null}
     * @return a new {@code Attr} instance
     */
    public static Attr of(String key, Object value) {
        return new Attr(key, value);
    }

    /**
     * Resolves a value, unwrapping {@link ThrowingSupplier} instances if needed.
     * If the supplier throws, the exception message is returned as the value.
     *
     * @param v the value to resolve
     * @return the resolved value
     */
    static Object resolveValue(Object v) {
        if (v instanceof ThrowingSupplier<?> s) {
            try {
                return s.get();
            } catch (Exception e) {
                return "<error: " + e.getMessage() + ">";
            }
        }
        return v;
    }

    /**
     * Returns the resolved value, unwrapping {@link ThrowingSupplier} instances if needed.
     * If the supplier throws, the exception message is returned.
     *
     * @return the resolved value, or the raw value if not a {@code ThrowingSupplier}
     */
    @Override
    public Object value() {
        return resolveValue(value);
    }

    /**
     * Returns the value as a string, using {@link String#valueOf(Object)}.
     *
     * @return the string representation of the value, or {@code null} if the value is {@code null}
     */
    public String valueAsString() {
        Object v = value();
        return v == null ? null : String.valueOf(v);
    }
}
