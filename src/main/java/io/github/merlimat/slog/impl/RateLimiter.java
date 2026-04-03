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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-call-site rate limiting for {@code onceEvery(N)} and {@code onceEvery(Duration)}.
 *
 * <p>State is keyed by call site (declaring class + line number), identified via
 * {@link StackWalker}. Each unique call site gets its own counter or timestamp.
 *
 * <p>Methods return the number of skipped occurrences since the last emission,
 * or {@code -1} if the call should be suppressed.
 */
final class RateLimiter {

    /** Returned when the call should be suppressed (not emitted). */
    static final long SUPPRESSED = -1;

    private static final StackWalker WALKER =
            StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    /** Classes in the internal call chain that should be skipped when resolving the call site. */
    private static final Set<String> INTERNAL_CLASSES = Set.of(
            RateLimiter.class.getName(),
            EventImpl.class.getName(),
            NoopEvent.class.getName()
    );

    private static final ConcurrentHashMap<Long, AtomicLong> COUNTERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, AtomicLong> TIMESTAMPS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, AtomicLong> DURATION_SKIP_COUNTERS = new ConcurrentHashMap<>();

    private RateLimiter() {}

    /**
     * Returns the number of skipped occurrences if this call site should emit
     * (1-in-N sampling), or {@link #SUPPRESSED} if suppressed.
     * The first invocation from any call site always emits (with 0 skipped).
     */
    static long checkEvery(int n) {
        long key = callSiteKey();
        AtomicLong counter = COUNTERS.computeIfAbsent(key, k -> new AtomicLong());
        long count = counter.getAndIncrement();
        if (count % n == 0) {
            return count == 0 ? 0 : n - 1;
        }
        return SUPPRESSED;
    }

    /**
     * Returns the number of skipped occurrences if this call site should emit
     * (time-based rate limiting), or {@link #SUPPRESSED} if suppressed.
     * The first invocation from any call site always emits (with 0 skipped).
     */
    static long checkAtMostEvery(long durationMillis, long nowMillis) {
        long key = callSiteKey();
        AtomicLong lastEmit = TIMESTAMPS.computeIfAbsent(key, k -> new AtomicLong());
        AtomicLong skipCounter = DURATION_SKIP_COUNTERS.computeIfAbsent(key, k -> new AtomicLong());

        long last = lastEmit.get();
        // last == 0 means first call (epoch millis is never 0 in practice)
        if (last == 0 || nowMillis - last >= durationMillis) {
            if (lastEmit.compareAndSet(last, nowMillis)) {
                return skipCounter.getAndSet(0);
            }
        }
        skipCounter.incrementAndGet();
        return SUPPRESSED;
    }

    private static long callSiteKey() {
        return WALKER.walk(s -> {
            StackWalker.StackFrame frame = s
                    .dropWhile(f -> INTERNAL_CLASSES.contains(f.getClassName()))
                    .findFirst()
                    .orElseThrow();
            return ((long) System.identityHashCode(frame.getDeclaringClass())) << 32
                    | (frame.getLineNumber() & 0xFFFFFFFFL);
        });
    }

    /** Clears all rate-limit state. Visible for testing. */
    static void reset() {
        COUNTERS.clear();
        TIMESTAMPS.clear();
        DURATION_SKIP_COUNTERS.clear();
    }
}
