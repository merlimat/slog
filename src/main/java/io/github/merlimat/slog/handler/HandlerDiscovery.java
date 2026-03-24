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
 * <p>If Log4j2 is on the classpath, a {@link Log4j2Handler} is used. Otherwise,
 * falls back to {@link Slf4jHandler}.
 */
public class HandlerDiscovery {
    private static final Handler INSTANCE = discover();

    public static Handler get() {
        return INSTANCE;
    }

    private static Handler discover() {
        try {
            Class.forName("org.apache.logging.log4j.LogManager");
            return new Log4j2Handler();
        } catch (ClassNotFoundException e) {
            return new Slf4jHandler();
        }
    }
}
