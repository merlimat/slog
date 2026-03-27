# slog — Structured Logging for Java

A lightweight structured logging library for Java, inspired by Go's [log/slog](https://pkg.go.dev/log/slog).

## Features

- **Structured key-value logging** — every log event carries typed attributes, not just a formatted string
- **Zero overhead when disabled** — level checks happen before any object allocation; disabled levels cost a single boolean check
- **Immutable context propagation** — derive loggers with `logger.with()` to attach attributes that are automatically included in every subsequent log call; parent attrs are shared, never copied
- **Cross-component context** — propagate context across component boundaries with `builder.ctx(otherLogger)`
- **Fluent event builder** — `log.info().attr("k", "v").log("msg")` for structured events; returns a no-op singleton when the level is disabled
- **Deferred logging** — `log.debug(e -> e.attr("k", v()).log(msg()))` wraps everything in a lambda that is only invoked when the level is enabled — ideal for expensive computations
- **Printf formatting** — `log.infof("Processed %d items", count)` and `log.info().logf(...)` with deferred formatting
- **Timed events** — automatically records elapsed duration
- **Backend auto-discovery** — delegates to Log4j2 (via ThreadContext) if available, falls back to SLF4J (via MDC); no hard runtime dependencies

## Requirements

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

// Timed events
Event e = log.info().timed();
executeQuery(sql);
e.attr("query", sql).log("Query executed");
// Automatically includes durationMs
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

When Log4j2 is on the classpath, structured attributes are placed into the
Log4j2 `ThreadContext`. With `JsonLayout` and `properties="true"`, each
attribute appears as an individual field inside the `contextMap` JSON object.
With `PatternLayout`, use `%X` for the full map or `%X{key}` for individual keys.

### SLF4J (fallback)

When only SLF4J is available, attributes are placed into the MDC for the duration
of each log call, making them available via pattern layouts or JSON encoders
(e.g. logstash-logback-encoder).

## Building

```bash
./gradlew build
```

## License

[Apache License 2.0](LICENSE)
