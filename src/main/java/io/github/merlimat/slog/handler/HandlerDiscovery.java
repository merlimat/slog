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
package io.github.merlimat.slog.handler;

import io.github.merlimat.slog.Handler;

/**
 * Auto-discovers the best available {@link Handler} implementation at startup.
 *
 * <p>Discovery order:
 * <ol>
 *   <li>Log4j2 — if {@code org.apache.logging.log4j.LogManager} is on the classpath</li>
 *   <li>SLF4J — if {@code org.slf4j.LoggerFactory} is on the classpath;
 *       uses {@link Slf4jSimpleHandler} when {@code slf4j-simple} is detected
 *       (since it does not render MDC), otherwise {@link Slf4jHandler}</li>
 * </ol>
 *
 * <p>If neither is found, an {@link IllegalStateException} is thrown.
 */
public class HandlerDiscovery {
    private HandlerDiscovery() {}

    private static final Handler INSTANCE = discover();

    /**
     * Returns the auto-discovered handler singleton.
     * @return the shared {@link Handler} instance
     * @throws IllegalStateException if no supported logging backend is on the classpath
     */
    public static Handler get() {
        return INSTANCE;
    }

    private static Handler discover() {
        try {
            Class.forName("org.apache.logging.log4j.LogManager");
            return new Log4j2Handler();
        } catch (ClassNotFoundException ignored) {
        }

        try {
            Class.forName("org.slf4j.LoggerFactory");
            if (isSlf4jSimple()) {
                return new Slf4jSimpleHandler();
            }
            return new Slf4jHandler();
        } catch (ClassNotFoundException ignored) {
        }

        throw new IllegalStateException(
                "No supported logging backend found. Add Log4j2 or SLF4J to the classpath.");
    }

    private static boolean isSlf4jSimple() {
        // SLF4J 2.x
        try {
            Class.forName("org.slf4j.simple.SimpleLogger");
            return true;
        } catch (ClassNotFoundException ignored) {
        }
        // SLF4J 1.x
        try {
            Class.forName("org.slf4j.impl.SimpleLogger");
            return true;
        } catch (ClassNotFoundException ignored) {
        }
        return false;
    }
}
