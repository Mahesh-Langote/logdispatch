# LogDispatch Spring Boot Starter

[![Maven Central](https://img.shields.io/maven-central/v/in.maheshlangote/logdispatch-spring-boot-starter)](https://central.sonatype.com/artifact/in.maheshlangote/logdispatch-spring-boot-starter)

A lightweight, zero-configuration **Application Performance Monitoring (APM) client** for Spring Boot. Uses Spring AOP to automatically intercept unhandled exceptions from `@RestController` classes and dispatches them asynchronously to your centralized log server.

---

## Features

- **Zero Code Changes** — Works out of the box with no changes to your controllers or exception handlers.
- **Asynchronous** — All log pushes run in a `CompletableFuture` fire-and-forget thread — zero impact on API response times.
- **Resilient** — Fails silently if the log server is unreachable. Your app never crashes due to a monitoring failure.
- **Multi-Tenant Ready** — Uses an `X-API-KEY` header to authenticate and route logs to the correct destination.
- **Customizable** — Use the `@LogDispatch` annotation to control how errors appear on your dashboard.

---

## Installation

Add to your `pom.xml`:

```xml
<dependency>
    <groupId>in.maheshlangote</groupId>
    <artifactId>logdispatch-spring-boot-starter</artifactId>
    <version>1.0.6</version>
</dependency>
```

---

## Configuration

Add to your `application.yml`:

```yaml
logdispatch:
  server-url: "https://your-apm-server.com/api/v1/ingest/logs"
  api-key: "your-secret-api-key"
  masked-headers: "authorization,cookie,x-api-key"
  exclude-paths: "/health,/actuator/**,/metrics/**"
```

| Property                     | Required | Description                                                              |
|------------------------------|----------|--------------------------------------------------------------------------|
| `logdispatch.server-url`     | ✅ Yes   | Full URL of the APM ingest endpoint                                      |
| `logdispatch.api-key`        | ✅ Yes   | API key used to authenticate with the APM server                         |
| `logdispatch.masked-headers` | ❌ No    | Comma-separated list of headers to mask. Defaults to empty (none masked) |
| `logdispatch.exclude-paths`  | ❌ No    | Comma-separated list of URI paths to exclude from logging. Supports wildcard patterns (e.g., `/health,/actuator/**`). Defaults to empty (all paths logged) |

---

## How It Works

When a `@RestController` method throws an unhandled exception, or when a filter rejects a request (e.g., `403 Forbidden`, `404 Not Found`), the SDK:

1. Captures the **request URI**, **HTTP method**, **exception class**, **message**, and **full stack trace**.
2. Reads optional metadata from the `@LogDispatch` annotation (feature, api, function names).
3. Asynchronously `POST`s a JSON payload to `server-url` with `X-API-KEY` in the header.
4. Logs a `WARN` to your application log if the push fails — and continues silently.

---

## What This SDK Sends

Every exception is pushed as a `POST` request to the configured `server-url`.

### Request Headers

| Header          | Value                     |
|-----------------|---------------------------|
| `Content-Type`  | `application/json`        |
| `X-API-KEY`     | Value of `logdispatch.api-key` |

### Request Body

```json
{
  "timestamp":        "2026-05-28T17:58:43.805Z",
  "errorType":        "IllegalArgumentException",
  "statusCode":       500,
  "errorMessage":     "Invalid entries",
  "errorPath":        "/api/v1/user/create",
  "affectedFeature":  "UserController",
  "affectedAPI":      "/api/v1/user/create",
  "apiType":          "POST",
  "affectedFunction": "createUser",
  "stackTrace":       "java.lang.IllegalArgumentException: Invalid entries\n\tat com.example...",
  "severity":         "CRITICAL",
  "inputInformation": {
    "queryString": null,
    "parameters": {},
    "headers": {
      "host": "localhost:8080",
      "content-type": "application/json"
    },
    "body": "{\"entries\": []}"
  }
}
```

| Field               | Type     | Description                                                         |
|---------------------|----------|---------------------------------------------------------------------|
| `timestamp`         | `String` | ISO-8601 UTC timestamp of when the exception occurred               |
| `errorType`         | `String` | Simple class name of the exception (e.g. `NullPointerException`)    |
| `statusCode`        | `Number` | HTTP status code — `500` for unhandled, `4xx` for known errors      |
| `errorMessage`      | `String` | The exception's `.getMessage()` value                               |
| `errorPath`         | `String` | The request URI where the exception was thrown                      |
| `affectedFeature`   | `String` | Controller class name, or value from `@LogDispatch(feature = "...")` |
| `affectedAPI`       | `String` | Request path, or value from `@LogDispatch(api = "...")`             |
| `apiType`           | `String` | The HTTP request method (e.g., `GET`, `POST`, `PUT`, `DELETE`)      |
| `affectedFunction`  | `String` | Method name, or value from `@LogDispatch(function = "...")`         |
| `stackTrace`        | `String` | Full stack trace as a newline-separated string                      |
| `severity`          | `String` | `CRITICAL` for 5xx, `WARNING` for 4xx                              |
| `inputInformation`  | `Object` | Request details (query params, headers, and body up to 32 KB)       |

> **Note:** For safety, `inputInformation.body` is skipped for `multipart/form-data` uploads or if the payload exceeds 32 KB to prevent `OutOfMemory` issues.

### Severity Mapping

| HTTP Status | Severity     |
|-------------|--------------|
| `4xx`       | `WARNING`    |
| `5xx`       | `CRITICAL`   |

---

## Server Health Check

The starter automatically exposes a public, lightweight HTTP endpoint that your APM server can poll to check if the application is alive and calculate its total uptime.

**Endpoint:** `GET /logdispatch/health`

**Response (`200 OK`):**
```json
{
  "status": "UP",
  "startupTime": "2026-05-31T02:00:00.000Z",
  "uptimeSeconds": 120
}
```

> **Note:** To prevent abuse, this endpoint has a built-in strict rate limiter of **60 requests per minute per IP address**. Exceeding this limit will return a `429 Too Many Requests` response.

---

## What Your Server Must Return

Your APM ingest endpoint must comply with the following contract for the SDK to behave correctly.

### ✅ Success — `2xx`

Any `2xx` response is treated as success. The SDK does not process the response body.

### ❌ `401 Unauthorized` — Bad API Key

Return a JSON body describing the issue. The SDK will log it as:

```
WARN [LogDispatch] Failed to push error: 401 UNAUTHORIZED : {"status":401,"error":"Unauthorized","message":"..."}
```

### ❌ `4xx` / `5xx` — Any other error

The SDK catches these, extracts the full response body, and logs at `WARN` level:

```
WARN [LogDispatch] Failed to push error: 500 INTERNAL_SERVER_ERROR : {"status":500,...}
```

### ❌ Network / Connection Error

If the server is unreachable, the SDK logs at `WARN` level and continues:

```
WARN [LogDispatch] Failed to push error: Connection refused: connect
```

> **Important:** The SDK **never rethrows** any exception. It always fails silently so your application is never affected.

---

## Optional: `@LogDispatch` Annotation

Override the default metadata (class name / method name / request path) with human-readable labels:

```java
import in.maheshlangote.logdispatch.annotation.LogDispatch;

@RestController
@LogDispatch(feature = "Payment Gateway")
public class PaymentController {

    @PostMapping("/pay")
    @LogDispatch(api = "Process Payment", function = "handlePayment")
    public void handlePayment() {
        // ...
    }
}
```

If an exception is thrown here, the payload will contain:

```json
{
  "affectedFeature":  "Payment Gateway",
  "affectedAPI":      "Process Payment",
  "affectedFunction": "handlePayment"
}
```

Without the annotation, it defaults to the controller class name, method name, and raw request URI.

---

## License

MIT License
