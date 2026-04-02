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
import io.github.merlimat.slog.LoggerBuilder;
import io.github.merlimat.slog.ThrowingSupplier;
import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of {@link LoggerBuilder}.
 */
final class LoggerBuilderImpl implements LoggerBuilder {
    private final BaseLogger parent;
    private final List<Attr> attrs = new ArrayList<>();
    private AttrChain inherited = AttrChain.EMPTY;

    LoggerBuilderImpl(Logger parent) {
        this.parent = (BaseLogger) parent;
    }

    @Override
    public LoggerBuilder ctx(Logger other) {
        AttrChain otherAttrs = ((BaseLogger) other).contextAttrs();
        if (!otherAttrs.isEmpty()) {
            inherited = otherAttrs.withPrefix(inherited);
        }
        return this;
    }

    @Override
    public LoggerBuilder attr(String key, Object value) {
        attrs.add(Attr.of(key, value));
        return this;
    }

    @Override
    public LoggerBuilder attr(String key, ThrowingSupplier<?> value) {
        attrs.add(Attr.of(key, value));
        return this;
    }

    @Override
    public Logger build() {
        AttrChain chain = parent.contextAttrs();
        if (!inherited.isEmpty()) {
            chain = chain.withPrefix(inherited);
        }
        if (!attrs.isEmpty()) {
            chain = chain.with(attrs);
        }
        if (chain == parent.contextAttrs()) {
            return parent;
        }
        return parent.derive(chain);
    }
}
