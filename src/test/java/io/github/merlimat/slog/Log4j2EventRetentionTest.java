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

import static org.junit.jupiter.api.Assertions.fail;

import java.lang.ref.WeakReference;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the pooled log event does not retain references after the emit
 * returns: the throwable and attr values of the last event must become
 * collectable while the thread idles, instead of lingering in the thread-local
 * pool until the next log call.
 */
class Log4j2EventRetentionTest {
    private static final String LOGGER_NAME = "io.github.merlimat.slog.RetentionTest";

    @Test
    void pooledEventReleasesThrowableAndAttrValuesAfterEmit() throws Exception {
        Logger log = Logger.get(LOGGER_NAME);

        Throwable thrown = new RuntimeException("boom");
        Object attrValue = new Object();
        WeakReference<Throwable> thrownRef = new WeakReference<>(thrown);
        WeakReference<Object> attrRef = new WeakReference<>(attrValue);

        log.error().attr("payload", attrValue).exception(thrown).log("with exception");

        thrown = null;
        attrValue = null;

        awaitCollected(thrownRef, "throwable");
        awaitCollected(attrRef, "attr value");
    }

    private static void awaitCollected(WeakReference<?> ref, String what) throws InterruptedException {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (ref.get() != null) {
            if (System.nanoTime() > deadline) {
                fail("the " + what + " of the last event is still strongly reachable "
                        + "after the emit returned");
            }
            System.gc();
            Thread.sleep(10);
        }
    }
}
