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

/**
 * A supplier of results that may throw a checked exception.
 *
 * <p>This is the throwing variant of {@link java.util.function.Supplier},
 * designed for use with slog's deferred evaluation. When the supplier is
 * invoked at emit time, any exception is caught and the exception message
 * is used as the attribute value instead.
 *
 * <pre>{@code
 * log.info()
 *     .attr("config", () -> loadConfig())   // loadConfig() throws IOException
 *     .log("Started");
 * // If loadConfig() fails: config attribute becomes the exception message
 * }</pre>
 *
 * @param <T> the type of results supplied by this supplier
 */
@FunctionalInterface
public interface ThrowingSupplier<T> {

    /**
     * Gets a result, potentially throwing a checked exception.
     *
     * @return a result
     * @throws Exception if unable to compute a result
     */
    T get() throws Exception;
}
