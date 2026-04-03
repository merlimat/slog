# slog — Structured Logging for Java

A lightweight structured logging library for Java, inspired by Go's [log/slog](https://pkg.go.dev/log/slog).

## Features

- **Structured key-value logging** — every log event carries typed attributes, not just a formatted string
- **Zero overhead when disabled** — level checks use a cached generation-counter scheme; disabled levels cost a single integer comparison with no volatile fence or framework call
- **Immutable context propagation** — derive loggers with `logger.with()` to attach attributes that are automatically included in every subsequent log call; parent attrs are shared, never copied
- **Cross-component context** — propagate context across component boundaries with `builder.ctx(otherLogger)`
- **Fluent event builder** — `log.info().attr("k", "v").log("msg")` for structured events; returns a no-op singleton when the level is disabled
- **Deferred logging** — `log.debug(e -> e.attr("k", v()).log(msg()))` wraps everything in a lambda that is only invoked when the level is enabled — ideal for expensive computations
- **Throwing suppliers** — attribute and message suppliers may throw checked exceptions; failures are caught and the exception message is recorded as the value, so a failing supplier never crashes the application
- **Printf formatting** — `log.infof("Processed %d items", count)` and `log.info().logf(...)` with deferred formatting
- **Rate limiting** — `onceEvery(N)` and `onceEvery(Duration)` throttle noisy log statements per call site with automatic `skipped` count tracking
- **Timed events** — automatically records elapsed duration
- **Backend auto-discovery** — delegates to Log4j2 if available, falls back to SLF4J; no hard runtime dependencies

## Documentation

- [Javadoc](https://merlimat.github.io/slog/javadoc/)

## Installation

**Gradle**
```kotlin
implementation("io.github.merlimat.slog:slog:0.9.5")
```

**Maven**
```xml
<dependency>
    <groupId>io.github.merlimat.slog</groupId>
    <artifactId>slog</artifactId>
    <version>0.9.5</version>
</dependency>
```

### Requirements

- Java 17+
- At least one of the following logging backends on the classpath:
  - **Log4j2** (preferred when available)
  - **SLF4J 1.x or 2.x**

## Quick Start

```java
import io.github.merlimat.slog.Logger;

// Simple messages
Logger log = Logger.get(MyService.class);
log.info("Server started");
log.infof("Listening on port %d", port);

// Fluent builder for structured events
log.info()
    .attr("method", "GET")
    .attr("path", "/api/orders")
    .attr("status", 200)
    .log("Request handled");

// Exceptions — full stack trace or message only
log.error()
    .attr("orderId", orderId)
    .exception(cause)
    .log("Order processing failed");

log.warn()
    .exceptionMessage(cause)    // just the message, no stack trace
    .log("Retrying operation");

// Deferred logging — lambda is only called if the level is enabled
log.debug(e -> e.attr("payload", serialize(data)).log("Request detail"));
log.debug(e -> e
    .attr("key", expensiveValue())
    .attr("dump", generateDump())
    .log(expensiveMessage()));

// Lazy attributes — supplier is only invoked at emit time
log.info()
    .attr("snapshot", () -> generateExpensiveSnapshot())
    .log("State captured");

// Throwing suppliers — checked exceptions are caught gracefully;
// the exception message becomes the attribute value
log.info()
    .attr("config", () -> loadConfigFromDisk())  // throws IOException
    .log("Service started");
// If loadConfigFromDisk() fails: config="<error: file not found>"

// Timed events
Event e = log.info().timed();
executeQuery(sql);
e.attr("query", sql).log("Query executed");
// Automatically includes durationMs
```

## Rate Limiting

Throttle noisy log statements directly at the call site — no external filter
configuration required. When a call is suppressed, a no-op singleton is returned
so all subsequent `attr()` and `log()` calls in the chain are free.

```java
// Count-based: emit once every 1000 calls from this site
log.info().onceEvery(1000)
    .attr("item", item)
    .log("Processing");

// Time-based: emit at most once every 30 seconds from this site
log.warn().onceEvery(Duration.ofSeconds(30))
    .attr("queueDepth", queue.size())
    .log("Queue backlog growing");
```

Both overloads are keyed by call site (class + line number), so different log
statements maintain independent counters. The first invocation from any call site
always emits. When calls have been suppressed, the emitted event automatically
includes a `skipped` attribute with the number of suppressed occurrences:

```
INFO  Processing {skipped=999, item=...}
```

## Context Propagation

The `with()` builder returns a new immutable logger — the original is never modified.
Parent attributes are shared by reference, never copied. This is designed for
component-scoped logging where you want certain attributes attached to every log
call without repeating them:

```java
public class Producer {
    private final Logger log;

    public Producer(String topic, String clientAddr, String namespace) {
        this.log = Logger.get(Producer.class).with()
            .attr("topic", topic)
            .attr("clientAddr", clientAddr)
            .attr("namespace", namespace)
            .build();
    }

    public void publish(Message msg) {
        log.info()
            .attr("msgId", msg.id())
            .attr("size", msg.size())
            .log("Published");
        // Output includes: topic, clientAddr, namespace, msgId, size
    }
}
```

### Cross-Component Context

Use `ctx()` to inherit context from another logger across component boundaries:

```java
Logger producerLog = Logger.get(Producer.class).with()
    .attr("topic", topic)
    .attr("clientAddr", addr)
    .build();

Logger consumerLog = Logger.get(Consumer.class).with()
    .ctx(producerLog)           // inherits topic, clientAddr
    .attr("subscription", sub)  // adds own attrs
    .build();

// Multiple ctx() calls append in order
Logger combined = Logger.get(Pipeline.class).with()
    .ctx(producerLog)
    .ctx(requestLog)
    .attr("step", "transform")
    .build();
```

### Duplicate Keys

When the same key appears at multiple levels (parent context, inherited via `ctx()`,
builder attrs, or per-event attrs), all occurrences are preserved in order.
Resolution (last-writer-wins, etc.) is left to the logging backend.

## Backend Behavior

### Log4j2 (preferred)

When Log4j2 is on the classpath, structured attributes are set directly on the
log event's context data — bypassing `ThreadContext` entirely for maximum
performance. With `JsonLayout` and `properties="true"`, each attribute appears
as an individual field inside the `contextMap` JSON object. With `PatternLayout`,
use `%X` for the full map or `%X{key}` for individual keys.

### SLF4J (fallback)

When only SLF4J is available, attributes are placed into the MDC for the duration
of each log call, making them available via pattern layouts or JSON encoders
(e.g. logstash-logback-encoder).

## Lombok Integration

If you use [Lombok](https://projectlombok.org/), you can use `@CustomLog` to generate the logger field
automatically. Add this to your `lombok.config`:

```properties
lombok.log.custom.declaration = io.github.merlimat.slog.Logger io.github.merlimat.slog.Logger.get(TYPE)
```

Then annotate your classes:

```java
@CustomLog
public class MyService {
    public void process() {
        log.info("hello");
    }
}
```

Lombok will generate `private static final Logger log = Logger.get(MyService.class);`.

## Performance

slog is designed to add minimal overhead on top of the underlying logging framework.
JMH benchmarks compare slog against direct Log4j2, SLF4J, and Flogger calls, all
writing to a Null appender (measuring framework overhead, not I/O). Root logger level
is INFO.

### Disabled path (TRACE call with INFO level) — ops/μs, higher is better

```
 slog Simple           ████████████████████████████████████████████  1005.4
 slog Fluent           ████████████████████████████████████████████  1005.0
 Log4j2 Simple         ██████████████████                            413.1
 Log4j2 Positional     ██████████████████                            413.2
 SLF4J Simple          ████████████████                              367.5
 Flogger Simple        ████████████████                              360.3
 SLF4J Positional      ███████████████                               360.5
 SLF4J Fluent          ███████████████                               359.6
 Flogger Positional    ███████████████                               344.9
```

When the level is disabled, slog checks a cached effective level using a
generation-counter scheme with `VarHandle.getOpaque()` — no volatile fence,
no call into the Log4j2 hierarchy. This makes the disabled path **2.4× faster**
than Log4j2 and **2.8× faster** than SLF4J. The fluent API returns a `NoopEvent`
singleton, so `attr()` and `log()` calls are no-ops with zero allocation.

### Enabled path (INFO call) — ops/μs, higher is better

```
 slog Simple           ████████████████████████████████████████████   24.6
 Log4j2 Simple         ██████████████████████████                     14.6
 slog Fluent           ███████████████████████                        13.2
 slog Fluent+Ctx       █████████████████████                          12.0
 SLF4J Simple          ████████████████████                           11.2
 SLF4J Positional      ████████████                                    7.1
 Log4j2 Positional     ███████████                                     6.4
 SLF4J Fluent          ███████                                         4.2
 Flogger Simple        █                                               0.7
 Flogger Positional    █                                               0.7
```

**slog Simple** (no structured attrs) is **1.7× faster** than native Log4j2 and
**2.2× faster** than SLF4J. The emit path builds a `MutableLogEvent` directly and
calls `LoggerConfig.log()`, completely bypassing `ThreadContext` and the
`ContextDataInjector` pipeline. The event and context map are pooled in
`ThreadLocal`s for zero allocation on the simple path.

**slog Fluent** (3 structured key-value attributes) runs at **13.2 ops/μs** —
**3.2× faster** than SLF4J's fluent API (4.2 ops/μs) and **2× faster** than
Log4j2 positional logging (6.4 ops/μs), which also carries 3 values but as
interpolated strings rather than structured data. Event attributes are stored
in inline parallel arrays, avoiding `ArrayList` and per-attribute object
allocation.

### Allocation rate (enabled path) — B/op, lower is better

```
 slog Simple           ▏                                                  0
 Log4j2 Simple         █                                                 24
 SLF4J Simple          █                                                 24
 Log4j2 Positional     █                                                 40
 slog Fluent+Ctx       █                                                 40
 SLF4J Positional      ██                                                72
 slog Fluent           ██                                                80
 SLF4J Fluent          ██████████████████████████                      1104
 Flogger Simple        ██████████████████████████████████████          1624
 Flogger Positional    ████████████████████████████████████████████    1904
```

**slog Simple** achieves **zero allocation** — the `MutableLogEvent`, message, and
context map are all pooled in `ThreadLocal`s, so the enabled simple path produces
no garbage at all.

**slog Fluent** allocates **80 B/op** (two small arrays for event attributes plus
autoboxing of one `int` argument), compared to **1,104 B/op** for SLF4J's fluent
API — a **14× reduction** in garbage produced per log call.

**Flogger** allocates **1,624–1,904 B/op** and achieves only **0.7 ops/μs** on the
enabled path — **35× slower** than slog Simple and **6× slower** than SLF4J Fluent.
The overhead comes from Flogger's backend translation layer (Log4j2 backend) and
heavy per-call allocation.

### Running the benchmarks

```bash
./gradlew :benchmark:jmh
```

Results are written to `benchmark/build/results/jmh/results.txt`.

To generate async-profiler flame graphs alongside the benchmarks:

```bash
./gradlew :benchmark:jmh -PjmhProfilers='async:libPath=/path/to/libasyncProfiler.dylib;output=flamegraph;dir=profile-results'
```

## Building

```bash
./gradlew build
```

## License

[Apache License 2.0](LICENSE)
