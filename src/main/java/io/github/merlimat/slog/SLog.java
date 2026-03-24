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
import java.time.Clock;
import java.util.List;

/**
 * Entry point for creating structured loggers.
 *
 * <p>Typical usage:
 * <pre>{@code
 * private static final Logger slog = SLog.getLogger(MyClass.class);
 * }</pre>
 *
 * <p>The returned {@link Logger} automatically discovers the best available backend
 * (Log4j2 if on the classpath, otherwise SLF4J).
 */
public final class SLog {

    private SLog() {
    }

    /**
     * Creates a new structured logger with the given name.
     *
     * @param name the logger name (typically a fully-qualified class name)
     * @return a new {@link Logger} instance
     */
    public static Logger getLogger(String name) {
        return new Logger(name, HandlerDiscovery.get(), List.of(), Clock.systemUTC());
    }

    /**
     * Creates a new structured logger named after the given class.
     *
     * @param clazz the class whose name will be used as the logger name
     * @return a new {@link Logger} instance
     */
    public static Logger getLogger(Class<?> clazz) {
        return getLogger(clazz.getName());
    }

    // Visible for testing
    static Logger getLogger(String name, Handler handler) {
        return new Logger(name, handler, List.of(), Clock.systemUTC());
    }

    static Logger getLogger(String name, Handler handler, Clock clock) {
        return new Logger(name, handler, List.of(), clock);
    }
}
