# slog — Structured Logging for Java

A lightweight structured logging library for Java, inspired by Go's [log/slog](https://pkg.go.dev/log/slog).

## Features

- **Structured key-value logging** — every log event carries typed attributes, not just a formatted string
- **Zero overhead when disabled** — level checks happen before any object allocation; disabled levels cost a single boolean check
- **Immutable context propagation** — derive loggers with `logger.with(...)` to attach attributes (e.g. topic, client address) that are automatically included in every subsequent log call
- **Fluent event builder** — `logger.atInfo().attr("k", "v").log("msg")` for complex events; returns a no-op singleton when the level is disabled
- **Timed events** — `logger.atInfo().timed().log("done")` automatically records elapsed duration
- **Backend auto-discovery** — delegates to Log4j2 (via `StringMapMessage`) if available, otherwise falls back to SLF4J (via MDC)

## Requirements

- Java 17+
- SLF4J 2.x on the classpath (required)
- Log4j2 on the classpath (optional, preferred when available)

## Quick Start

```java
import io.github.merlimat.slog.SLog;
import io.github.merlimat.slog.Logger;

// Create a logger
Logger log = SLog.getLogger(MyService.class);

// Simple message
log.info("Server started");

// Message with inline key-value pairs
log.info("Request handled", "method", "GET", "path", "/api", "status", 200);

// Derive a logger with context — all subsequent logs include these attributes
Logger producerLog = log
    .with("topic", "persistent://public/default/orders")
    .with("clientAddr", "10.0.0.1");

producerLog.info("Message published", "msgId", "1:2:3");
// Output includes: topic, clientAddr, and msgId

// Fluent builder for complex events
log.atInfo()
    .attr("orderId", orderId)
    .attr("items", itemCount)
    .exception(cause)
    .log("Order processing failed");

// Timed events
Event e = log.atInfo().timed();

executeQuery(sql);

e.attr("query", sql)
    .log("Query executed");
// Automatically includes durationMs
```

## Context Propagation

The `with()` method returns a new immutable logger — the original is never modified.
This is designed for component-scoped logging where you want certain attributes
attached to every log call without repeating them:

```java
public class Producer {
    private final Logger log;

    public Producer(String topic, String clientAddr) {
        this.log = SLog.getLogger(Producer.class)
            .with("topic", topic)
            .with("clientAddr", clientAddr)
            .with("namespace", namespace);

        // Every log call from this.log automatically includes
        // topic, clientAddr, and namespace
    }

    public void publish(Message msg) {
        log.info("Published", "msgId", msg.id(), "size", msg.size());
        // Outputs: topic=..., clientAddr=..., namespace=..., msgId=..., size=...
    }
}
```

## Backend Behavior

### Log4j2 (preferred)

When Log4j2 is on the classpath, attributes are emitted as a `StringMapMessage`,
which integrates natively with JSON Template Layout for structured JSON output.

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
