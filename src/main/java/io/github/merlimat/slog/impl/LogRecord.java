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

import java.time.Duration;

/**
 * An immutable snapshot of a structured log event, ready for emission by the logging backend.
 *
 * <p>A LogRecord is assembled by the {@link io.github.merlimat.slog.Logger} (for direct calls)
 * or by {@link io.github.merlimat.slog.Event#log(String)} (for the fluent builder), and then
 * passed to the backend-specific {@code emit} method.
 *
 * @param loggerName the name of the logger that produced this record
 * @param level      the severity level
 * @param message    the human-readable log message
 * @param attrs      structured key-value attributes (context + per-event); may be a
 *                   zero-copy composite view backed by the original lists
 * @param throwable  an optional attached exception, may be {@code null}
 * @param duration   elapsed time if the event was {@linkplain io.github.merlimat.slog.Event#timed() timed},
 *                   otherwise {@code null}
 * @param callerFqcn fully-qualified class name of the last slog frame, used by
 *                   handlers to resolve the correct caller location
 */
record LogRecord(
        String loggerName,
        Level level,
        String message,
        Iterable<Attr> attrs,
        Throwable throwable,
        Duration duration,
        String callerFqcn
) {

    boolean hasContext() {
        return (attrs != null && attrs != AttrChain.EMPTY) || duration != null;
    }
}
