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
 * Log severity levels, ordered from least to most severe.
 */
public enum Level {
    /** Finest-grained informational events. */
    TRACE,
    /** Fine-grained informational events useful for debugging. */
    DEBUG,
    /** Informational messages highlighting progress of the application. */
    INFO,
    /** Potentially harmful situations. */
    WARN,
    /** Error events that might still allow the application to continue. */
    ERROR
}
