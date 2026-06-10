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

import java.util.Arrays;
import java.util.List;

/**
 * An immutable, flattened sequence of context attributes, used internally by
 * {@link io.github.merlimat.slog.Logger}.
 *
 * <p>Loggers are built once and emit many times, so derivation flattens the
 * parent's attrs and the new ones into a single array up front: the emit path
 * iterates by index with no per-event allocation. Attrs are kept in
 * root-to-child order (parent attrs first).
 */
final class AttrChain {

    public static final AttrChain EMPTY = new AttrChain(new Attr[0]);

    private final Attr[] attrs;

    private AttrChain(Attr[] attrs) {
        this.attrs = attrs;
    }

    public AttrChain with(List<Attr> more) {
        if (more.isEmpty()) {
            return this;
        }
        Attr[] result = Arrays.copyOf(attrs, attrs.length + more.size());
        for (int i = 0; i < more.size(); i++) {
            result[attrs.length + i] = more.get(i);
        }
        return new AttrChain(result);
    }

    /**
     * Returns a new chain with {@code other}'s attrs before this chain's attrs.
     * Used to adopt another logger's context.
     */
    public AttrChain withPrefix(AttrChain other) {
        if (other.isEmpty()) {
            return this;
        }
        if (this.isEmpty()) {
            return other;
        }
        Attr[] result = Arrays.copyOf(other.attrs, other.attrs.length + attrs.length);
        System.arraycopy(attrs, 0, result, other.attrs.length, attrs.length);
        return new AttrChain(result);
    }

    public boolean isEmpty() {
        return attrs.length == 0;
    }

    int size() {
        return attrs.length;
    }

    Attr get(int index) {
        return attrs[index];
    }
}
