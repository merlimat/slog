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

import io.github.merlimat.slog.handler.HandlerDiscovery;

/**
 * Backend interface for processing structured log records.
 *
 * <p>A Handler is responsible for two things:
 * <ol>
 *   <li>Determining whether a given logger/level combination is enabled
 *       (via {@link #isEnabled}), allowing callers to short-circuit before
 *       allocating any log event objects.</li>
 *   <li>Emitting the final {@link LogRecord} to the underlying logging
 *       framework (via {@link #handle}).</li>
 * </ol>
 *
 * <p>Built-in implementations delegate to Log4j2 (if available on the classpath)
 * or SLF4J as a fallback.
 *
 * @see io.github.merlimat.slog.handler.Log4j2Handler
 * @see io.github.merlimat.slog.handler.Slf4jHandler
 */
public interface Handler {

    /**
     * Returns the auto-discovered handler for the current classpath.
     *
     * @return the shared {@code Handler} instance
     */
    static Handler get() {
        return HandlerDiscovery.get();
    }

    /**
     * Checks whether logging is enabled for the given logger name and level.
     *
     * <p>This is called before any event construction, ensuring zero overhead
     * when the level is disabled.
     *
     * @param loggerName the logger name (typically a fully-qualified class name)
     * @param level      the log level to check
     * @return {@code true} if a log record at this level would be emitted
     */
    boolean isEnabled(String loggerName, Level level);

    /**
     * Processes a structured log record, emitting it to the underlying logging framework.
     *
     * @param record the log record to emit
     */
    void handle(LogRecord record);
}
