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

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * An immutable linked chain of attribute lists, used internally by {@link Logger}
 * to share parent context without copying.
 *
 * <p>Each node holds its own small list of attrs and a pointer to the parent node.
 * Iteration yields all attrs in root-to-child order (parent attrs first).
 */
final class AttrChain implements Iterable<Attr> {

    static final AttrChain EMPTY = new AttrChain(null, List.of());

    private final AttrChain parent;
    private final List<Attr> own;

    private AttrChain(AttrChain parent, List<Attr> own) {
        this.parent = parent;
        this.own = own;
    }

    AttrChain with(Attr attr) {
        return new AttrChain(this, List.of(attr));
    }

    AttrChain with(List<Attr> attrs) {
        if (attrs.isEmpty()) {
            return this;
        }
        return new AttrChain(this, List.copyOf(attrs));
    }

    boolean isEmpty() {
        return this == EMPTY;
    }

    @Override
    public Iterator<Attr> iterator() {
        if (this == EMPTY) {
            return Collections.emptyIterator();
        }

        // Collect chain nodes into an array (child → root), then iterate root → child.
        // Chain depth is typically very small (2-5 levels).
        int depth = 0;
        for (AttrChain n = this; n != EMPTY; n = n.parent) {
            depth++;
        }

        AttrChain[] nodes = new AttrChain[depth];
        int i = depth - 1;
        for (AttrChain n = this; n != EMPTY; n = n.parent) {
            nodes[i--] = n;
        }

        return new ChainIterator(nodes);
    }

    private static final class ChainIterator implements Iterator<Attr> {
        private final AttrChain[] nodes;
        private int nodeIndex;
        private int attrIndex;

        ChainIterator(AttrChain[] nodes) {
            this.nodes = nodes;
        }

        @Override
        public boolean hasNext() {
            while (nodeIndex < nodes.length) {
                if (attrIndex < nodes[nodeIndex].own.size()) {
                    return true;
                }
                nodeIndex++;
                attrIndex = 0;
            }
            return false;
        }

        @Override
        public Attr next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return nodes[nodeIndex].own.get(attrIndex++);
        }
    }
}
