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
package io.github.merlimat.slog.benchmark;

import io.github.merlimat.slog.Logger;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;

/**
 * JMH benchmarks comparing slog, SLF4J, and Log4j2 logging overhead.
 *
 * <p>All loggers write to a Null appender so we measure framework overhead,
 * not I/O. The root logger level is INFO, so INFO calls are "enabled" and
 * TRACE calls are "disabled".
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 1)
@Measurement(iterations = 2)
@Fork(1)
public class LoggingBenchmark {

    private Logger slog;
    private Logger slogWithContext;
    private org.slf4j.Logger slf4j;
    private org.apache.logging.log4j.Logger log4j2;

    @Setup
    public void setup() {
        slog = Logger.get("slog.bench");
        slogWithContext = slog.with()
                .attr("method", "GET")
                .attr("path", "/api/orders")
                .build();
        slf4j = org.slf4j.LoggerFactory.getLogger("slf4j.bench");
        log4j2 = org.apache.logging.log4j.LogManager.getLogger("log4j2.bench");
    }

    // ---------------------------------------------------------------
    // Enabled (INFO level)
    // ---------------------------------------------------------------

    @Benchmark
    public void slogSimple_enabled() {
        slog.info("Request processed");
    }

    @Benchmark
    public void slogFluent_enabled() {
        slog.info()
                .attr("method", "GET")
                .attr("path", "/api/orders")
                .attr("status", 200)
                .log("Request processed");
    }

    @Benchmark
    public void slogFluentWithContext_enabled() {
        slogWithContext.info()
                .attr("status", 200)
                .log("Request processed");
    }

    @Benchmark
    public void slf4jSimple_enabled() {
        slf4j.info("Request processed");
    }

    @Benchmark
    public void slf4jFluent_enabled() {
        slf4j.atInfo()
                .addKeyValue("method", "GET")
                .addKeyValue("path", "/api/orders")
                .addKeyValue("status", 200)
                .log("Request processed");
    }

    @Benchmark
    public void slf4jPositional_enabled() {
        slf4j.info("Request processed method={} path={} status={}", "GET", "/api/orders", 200);
    }

    @Benchmark
    public void log4j2Simple_enabled() {
        log4j2.info("Request processed");
    }

    @Benchmark
    public void log4j2Positional_enabled() {
        log4j2.info("Request processed method={} path={} status={}", "GET", "/api/orders", 200);
    }

    // ---------------------------------------------------------------
    // Disabled (TRACE level — root is INFO)
    // ---------------------------------------------------------------

    @Benchmark
    public void slogSimple_disabled() {
        slog.trace("Request processed");
    }

    @Benchmark
    public void slogFluent_disabled() {
        slog.trace()
                .attr("method", "GET")
                .attr("path", "/api/orders")
                .attr("status", 200)
                .log("Request processed");
    }

    @Benchmark
    public void slf4jSimple_disabled() {
        slf4j.trace("Request processed");
    }

    @Benchmark
    public void slf4jFluent_disabled() {
        slf4j.atTrace()
                .addKeyValue("method", "GET")
                .addKeyValue("path", "/api/orders")
                .addKeyValue("status", 200)
                .log("Request processed");
    }

    @Benchmark
    public void slf4jPositional_disabled() {
        slf4j.trace("Request processed method={} path={} status={}", "GET", "/api/orders", 200);
    }

    @Benchmark
    public void log4j2Simple_disabled() {
        log4j2.trace("Request processed");
    }

    @Benchmark
    public void log4j2Positional_disabled() {
        log4j2.trace("Request processed method={} path={} status={}", "GET", "/api/orders", 200);
    }
}
