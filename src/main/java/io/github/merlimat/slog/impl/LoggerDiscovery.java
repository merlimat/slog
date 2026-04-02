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
import java.time.Clock;
import java.util.List;
import java.util.Set;

/**
 * Auto-discovers the best available logging backend at startup and creates
 * the appropriate {@link Logger} subclass.
 *
 * <p>Discovery order:
 * <ol>
 *   <li>Log4j2 — if {@code org.apache.logging.log4j.LogManager} is on the classpath</li>
 *   <li>SLF4J — if {@code org.slf4j.LoggerFactory} is on the classpath;
 *       uses a simple-logger variant when {@code slf4j-simple} is detected
 *       (since it does not render MDC)</li>
 * </ol>
 *
 * <p>If neither is found, an {@link IllegalStateException} is thrown.
 */
public class LoggerDiscovery {
    private LoggerDiscovery() {}

    private enum Backend { LOG4J2, SLF4J, SLF4J_SIMPLE }

    private static final Backend BACKEND = discoverBackend();

    /**
     * Creates a new Logger bound to the discovered backend.
     *
     * @param name the logger name (typically a fully-qualified class name)
     * @return a new {@code Logger} instance for the discovered backend
     */
    public static Logger createLogger(String name) {
        return createLogger(name, AttrChain.EMPTY, Clock.systemUTC());
    }

    static Logger createLogger(String name, AttrChain contextAttrs, Clock clock) {
        return switch (BACKEND) {
            case LOG4J2 -> new Log4j2Logger(name, contextAttrs, clock);
            case SLF4J -> new Slf4jLogger(name, contextAttrs, clock);
            case SLF4J_SIMPLE -> new Slf4jSimpleLogger(name, contextAttrs, clock);
        };
    }

    /**
     * Creates a new {@link LoggerBuilder} for the given parent logger.
     *
     * @param parent the parent logger
     * @return a new builder
     */
    public static LoggerBuilder newBuilder(Logger parent) {
        return new LoggerBuilderImpl(parent);
    }

    /**
     * Creates a test logger that captures records into the given sink.
     *
     * @param name          the logger name
     * @param enabledLevels the set of enabled levels
     * @param sink          the list to collect emitted records
     * @return a new test logger
     */
    static Logger forTest(String name, Set<Level> enabledLevels, List<LogRecord> sink) {
        return BaseLogger.forTest(name, enabledLevels, sink);
    }

    static Logger forTest(String name, Set<Level> enabledLevels, List<LogRecord> sink, Clock clock) {
        return BaseLogger.forTest(name, enabledLevels, sink, clock);
    }

    private static Backend discoverBackend() {
        try {
            Class.forName("org.apache.logging.log4j.LogManager");
            return Backend.LOG4J2;
        } catch (ClassNotFoundException ignored) {
        }

        try {
            Class.forName("org.slf4j.LoggerFactory");
            if (isSlf4jSimple()) {
                return Backend.SLF4J_SIMPLE;
            }
            return Backend.SLF4J;
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
