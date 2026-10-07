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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Verifies that slog works when the optional LMAX Disruptor jar is absent.
 * The async-mode detection in the Log4j2 handler must not link
 * {@code AsyncLogger} (which implements a disruptor interface) in that case.
 *
 * <p>Run by the {@code noDisruptorTest} Gradle task, which filters the
 * disruptor jar off the test classpath.
 */
class NoDisruptorTest {

    @Test
    void loggingWorksWithoutDisruptorOnClasspath() {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.lmax.disruptor.RingBuffer"),
                "this test must run without the disruptor jar (noDisruptorTest task)");

        Logger log = Logger.get(NoDisruptorTest.class).with()
                .attr("component", "no-disruptor")
                .build();

        // Must not throw NoClassDefFoundError from the AsyncLogger detection
        log.info("plain message");
        log.info().attr("msgId", "1:2").timed().log("structured message");
    }
}
