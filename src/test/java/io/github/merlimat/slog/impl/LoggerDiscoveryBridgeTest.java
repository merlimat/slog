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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Regression test for https://github.com/merlimat/slog/issues/32: with log4j-api
 * routed to SLF4J by {@code log4j-to-slf4j} and no log4j-core, discovery must
 * pick SLF4J instead of failing with {@code NoClassDefFoundError}.
 */
public class LoggerDiscoveryBridgeTest {

    @Test
    void log4jToSlf4jBridgeSelectsSlf4j() throws Exception {
        List<URL> urls = new ArrayList<>();
        for (String property : List.of("slog.test.mainClasses", "slog.test.log4jToSlf4jClasspath")) {
            for (String path : System.getProperty(property).split(File.pathSeparator)) {
                urls.add(new File(path).toURI().toURL());
            }
        }

        try (URLClassLoader loader = new URLClassLoader(
                urls.toArray(new URL[0]), ClassLoader.getPlatformClassLoader())) {
            Object logger = loader.loadClass("io.github.merlimat.slog.Logger")
                    .getMethod("get", String.class)
                    .invoke(null, "test");
            assertEquals("io.github.merlimat.slog.impl.Slf4jLogger", logger.getClass().getName());
        }
    }
}
