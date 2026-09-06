# LogDispatch Spring Boot Starter

[![Maven Central](https://img.shields.io/maven-central/v/in.maheshlangote/logdispatch-spring-boot-starter)](https://central.sonatype.com/artifact/in.maheshlangote/logdispatch-spring-boot-starter)
[![CI](https://github.com/Mahesh-Langote/logdispatch/actions/workflows/logdispatch-pr-gate.yml/badge.svg?branch=main)](https://github.com/Mahesh-Langote/logdispatch/actions)
[![Java](https://img.shields.io/badge/Java-17%2B-blue?logo=openjdk)](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

A lightweight, zero-configuration **Application Performance Monitoring (APM) client** for Spring Boot.

It uses Spring AOP and Servlet Filters to automatically intercept unhandled exceptions, filter-level security errors, and developer execution debug logs, dispatching them asynchronously to your centralized APM server.

---

## Features

* **Zero Code Changes** — Works out of the box with no changes to your controllers or exception handlers.
* **Developer Log Capture** — Automatically captures `DEBUG`, `INFO`, `WARN`, and `ERROR` logs printed during the execution of a failing request.
* **Asynchronous** — All log pushes run in a `CompletableFuture` fire-and-forget thread with minimal impact on API response times.
* **Resilient** — Fails silently if the log server is unreachable. Your application never crashes because of monitoring failures.
* **Multi-Tenant Ready** — Uses an `X-API-KEY` header to authenticate and route logs correctly.
* **Customizable** — Use the `@LogDispatch` annotation to control how errors appear on your dashboard.

---

# Installation

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>in.maheshlangote</groupId>
    <artifactId>logdispatch-spring-boot-starter</artifactId>
    <version>1.0.9</version>
</dependency>
```

## Configuration

### application.yml

```yaml
logdispatch:
  enabled: true
  server-url: "https://your-apm-server.com/api/v1/ingest/logs"
  api-key: "your-secret-api-key"
  timeout-ms: 3000
  masked-headers: "authorization,cookie,x-api-key"
  exclude-paths: "/health,/actuator/**,/metrics/**"
  logs:
    enabled: true
    max-entries: 50
    min-level: "DEBUG"
    exclude-loggers:
      - "com.zaxxer.hikari"
```

### application.properties

```properties
logdispatch.enabled=true
logdispatch.server-url=https://your-apm-server.com/api/v1/ingest/logs
logdispatch.api-key=your-secret-api-key
logdispatch.timeout-ms=3000
logdispatch.masked-headers=authorization,cookie,x-api-key
logdispatch.exclude-paths=/health,/actuator/**,/metrics/**
logdispatch.logs.enabled=true
logdispatch.logs.max-entries=50
logdispatch.logs.min-level=DEBUG
logdispatch.logs.exclude-loggers=com.zaxxer.hikari
```

### Configuration Properties

| Property | Required | Default | Description |
| :--- | :--- | :--- | :--- |
| `logdispatch.enabled` | ❌ No | `true` | Enables or disables the LogDispatch SDK. |
| `logdispatch.server-url` | ✅ Yes, when enabled | `http://localhost:8081/...` | Full URL of the APM ingest endpoint. |
| `logdispatch.api-key` | ✅ Yes, when enabled | `default-key` | API key used to authenticate with the APM server. |
| `logdispatch.timeout-ms` | ❌ No | `3000` | Connection and read timeout in milliseconds. |
| `logdispatch.masked-headers` | ❌ No | `[]` | Comma-separated list of HTTP headers to mask (e.g. `authorization`). |
| `logdispatch.exclude-paths` | ❌ No | `[]` | Comma-separated list of URI paths to exclude (supports wildcards like `/actuator/**`). |
| `logdispatch.health.enabled` | ❌ No | `true` | Enables or disables registering the `/logdispatch/health` endpoint. |
| `logdispatch.logs.enabled` | ❌ No | `true` | Enables capturing developer execution debug logs during failing requests. |
| `logdispatch.logs.max-entries` | ❌ No | `50` | Maximum number of log lines to retain per request. |
| `logdispatch.logs.min-level` | ❌ No | `DEBUG` | Minimum log level to capture (`TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`). |
| `logdispatch.logs.exclude-loggers` | ❌ No | `[]` | List or comma-separated prefixes of logger names to ignore (e.g. `com.zaxxer.hikari`). |
| `logdispatch.logs.include-loggers` | ❌ No | `[]` | List or comma-separated prefixes of logger names to exclusively capture (empty captures all). |

Disable LogDispatch in local or test profiles when you want the dependency on the classpath but do not want any APM activity:

```yaml
# application-dev.yml
logdispatch:
  enabled: false

# application-prod.yml
logdispatch:
  enabled: true
  server-url: "https://apm.mycompany.com/ingest"
  api-key: "${APM_API_KEY}"
```

When `logdispatch.enabled=false`, the SDK passes requests through without inspecting or dispatching errors, and the health endpoint reports that LogDispatch is disabled.

---

# How It Works

When a `@RestController` method throws an unhandled exception, or when a filter rejects a request (e.g., `403 Forbidden`, `404 Not Found`):

1. LogDispatch initializes a `ThreadLocal` ring buffer at the start of the request.
2. Developer logs (`log.debug()`, `log.info()`, `log.warn()`, `log.error()`) executed during that request are captured into the buffer.
3. If an error occurs ($\ge 400$), the SDK captures the request URI, HTTP method, exception class, stack trace, and structured execution logs.
4. Asynchronously sends a JSON payload to the configured `server-url`.
5. Includes the `X-API-KEY` header for authentication.
6. Clears the thread-local buffer in a `finally` block to guarantee zero memory leakage.

---

# What This SDK Sends

Every exception is pushed as a `POST` request to the configured `server-url`.

## Request Headers

| Header | Value |
| :--- | :--- |
| `Content-Type` | `application/json` |
| `X-API-KEY` | Value of `logdispatch.api-key` |

## Request Body Example

```json
{
  "timestamp": "2026-05-28T17:58:43.805Z",
  "errorType": "IllegalArgumentException",
  "statusCode": 500,
  "errorMessage": "Invalid entries",
  "errorPath": "/api/v1/user/create",
  "affectedFeature": "UserController",
  "affectedAPI": "/api/v1/user/create",
  "apiType": "POST",
  "affectedFunction": "createUser",
  "stackTrace": "java.lang.IllegalArgumentException: Invalid entries\n\tat com.example...",
  "severity": "CRITICAL",
  "inputInformation": {
    "queryString": null,
    "parameters": {},
    "headers": {
      "host": "localhost:8080",
      "content-type": "application/json"
    },
    "body": "{\"entries\": []}"
  },
  "executionLogs": [
    {
      "timestamp": "2026-05-28T17:58:43.790Z",
      "level": "INFO",
      "loggerName": "com.example.controller.UserController",
      "threadName": "http-nio-8080-exec-1",
      "message": "Received request to create user",
      "throwable": null
    },
    {
      "timestamp": "2026-05-28T17:58:43.795Z",
      "level": "DEBUG",
      "loggerName": "com.example.service.UserService",
      "threadName": "http-nio-8080-exec-1",
      "message": "Validating input entries list...",
      "throwable": null
    },
    {
      "timestamp": "2026-05-28T17:58:43.802Z",
      "level": "ERROR",
      "loggerName": "com.example.service.UserService",
      "threadName": "http-nio-8080-exec-1",
      "message": "Validation failed: entries list cannot be empty",
      "throwable": null
    }
  ]
}
```

## Payload Fields

| Field | Type | Description |
| :--- | :--- | :--- |
| `timestamp` | String | ISO-8601 UTC timestamp |
| `errorType` | String | Exception class name or `FilterError` |
| `statusCode` | Number | HTTP status code (e.g. 400, 403, 500) |
| `errorMessage` | String | Exception message |
| `errorPath` | String | Request URI |
| `affectedFeature` | String | Controller name or `@LogDispatch` annotation override |
| `affectedAPI` | String | API path or `@LogDispatch` annotation override |
| `apiType` | String | HTTP method (`GET`, `POST`, etc.) |
| `affectedFunction` | String | Method name or `@LogDispatch` annotation override |
| `stackTrace` | String | Full stack trace string |
| `severity` | String | `WARNING` (4xx), `CRITICAL` (5xx), or `SECURITY` (Filter Error) |
| `inputInformation` | Object | Request metadata including headers, parameters, and body |
| `executionLogs` | Array | Structured array of developer logs printed during request execution |

> **Note:** `inputInformation.body` is skipped for `multipart/form-data` uploads or payloads larger than 32 KB.

---

## Severity Mapping

| HTTP Status / Condition | Severity |
| :--- | :--- |
| 4xx (Exception) | `WARNING` |
| 5xx (Exception) | `CRITICAL` |
| Filter/Routing Error | `SECURITY` |

---

# Server Health Check

The starter automatically exposes a lightweight endpoint that allows your APM server to verify application health and uptime.

### Endpoint

```http
GET /logdispatch/health
```

### Response

```json
{
  "status": "UP",
  "startupTime": "2026-05-31T02:00:00.000Z",
  "uptimeSeconds": 120
}
```

### Disabling the Health Endpoint

If your application uses a security layer (e.g. Spring Security, an API gateway) that requires all unauthenticated endpoints to be explicitly opted in, or you simply don't want the endpoint exposed, disable it entirely:

```yaml
logdispatch:
  health:
    enabled: false
```

```properties
logdispatch.health.enabled=false
```

When disabled, the `/logdispatch/health` endpoint is not registered at all — requests to that path receive a `404 Not Found`.

### Rate Limiting

To prevent abuse, the endpoint is limited to:

```text
60 requests per minute per IP
```

Requests exceeding the limit receive:

```http
429 Too Many Requests
```

---

# Expected Server Responses

Your APM ingest endpoint should follow this contract.

## Success (2xx)

Any `2xx` response is treated as successful. The SDK ignores the response body.

---

## Unauthorized (401)

Example response:

```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid API key"
}
```

SDK log:

```text
WARN [LogDispatch] Failed to push error: 401 UNAUTHORIZED : {"status":401,"error":"Unauthorized","message":"Invalid API key"}
```

---

## Other 4xx / 5xx Errors

Example SDK log:

```text
WARN [LogDispatch] Failed to push error: 500 INTERNAL_SERVER_ERROR : {"status":500,...}
```

---

## Network Failure

Example SDK log:

```text
WARN [LogDispatch] Failed to push error: Connection refused: connect
```

> **Important:** The SDK never rethrows exceptions. Monitoring failures never affect the application.

---

# Optional: @LogDispatch Annotation

Override default metadata with human-readable labels.

```java
import in.maheshlangote.logdispatch.annotation.LogDispatch;

@RestController
@LogDispatch(feature = "Payment Gateway")
public class PaymentController {

    @PostMapping("/pay")
    @LogDispatch(
        api = "Process Payment",
        function = "handlePayment"
    )
    public void handlePayment() {
        // ...
    }
}
```

Generated payload:

```json
{
  "affectedFeature": "Payment Gateway",
  "affectedAPI": "Process Payment",
  "affectedFunction": "handlePayment"
}
```

---

# Testing & Contributing

Please see [TESTING.md](TESTING.md) for detailed guidelines on how to run, structure, and write tests for this SDK.

---

# Troubleshooting

For common problems and solutions, see [TROUBLESHOOTING.md](TROUBLESHOOTING.md).

---

## Example App

A runnable Spring Boot demo is available in [example-app](./example-app). It includes REST endpoints that intentionally throw exceptions to demonstrate LogDispatch error and log capturing.

---

## License

MIT License
