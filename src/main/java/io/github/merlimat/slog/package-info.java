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

/**
 * Structured logging for Java, inspired by Go's
 * <a href="https://pkg.go.dev/log/slog">log/slog</a>.
 *
 * <p>The main entry point is {@link io.github.merlimat.slog.Logger}, which emits
 * structured log records with typed key-value attributes. Loggers are immutable
 * and thread-safe; derive scoped loggers with {@link io.github.merlimat.slog.Logger#with()}.
 *
 * <h2>Quick start</h2>
 * <pre>{@code
 * Logger log = Logger.get(MyService.class);
 *
 * // Simple message
 * log.info("Server started");
 *
 * // Structured attributes via fluent builder
 * log.info()
 *     .attr("orderId", orderId)
 *     .attr("items", itemCount)
 *     .log("Order processed");
 *
 * // Deferred logging — lambda only invoked when level is enabled
 * log.debug(e -> e.attr("payload", serialize(data)).log("detail"));
 *
 * // Context propagation
 * Logger scoped = log.with()
 *     .attr("requestId", reqId)
 *     .build();
 * }</pre>
 *
 * <h2>Key types</h2>
 * <ul>
 *   <li>{@link io.github.merlimat.slog.Logger} — creates and emits log events</li>
 *   <li>{@link io.github.merlimat.slog.Event} — fluent builder for a single log event</li>
 *   <li>{@link io.github.merlimat.slog.Level} — log severity levels</li>
 *   <li>{@link io.github.merlimat.slog.Handler} — backend SPI for routing log records</li>
 * </ul>
 *
 * <p>The library auto-discovers the logging backend at runtime: Log4j2 if available,
 * otherwise SLF4J. No hard runtime dependencies are required.
 */
package io.github.merlimat.slog;
