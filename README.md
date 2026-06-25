# LogDispatch Spring Boot Starter

[![Maven Central](https://img.shields.io/maven-central/v/in.maheshlangote/logdispatch-spring-boot-starter)](https://central.sonatype.com/artifact/in.maheshlangote/logdispatch-spring-boot-starter)
[![CI](https://github.com/Mahesh-Langote/logdispatch/actions/workflows/logdispatch-pr-gate.yml/badge.svg?branch=main)](https://github.com/Mahesh-Langote/logdispatch/actions)
[![Java](https://img.shields.io/badge/Java-17%2B-blue?logo=openjdk)](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

A lightweight, zero-configuration **Application Performance Monitoring (APM) client** for Spring Boot.

It uses Spring AOP to automatically intercept unhandled exceptions from `@RestController` classes and dispatch them asynchronously to your centralized log server.

---

## Features

* **Zero Code Changes** — Works out of the box with no changes to your controllers or exception handlers.
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
    <version>1.0.6</version>
</dependency>
```

## Configuration

### application.yml

```yaml
logdispatch:
  enabled: true
  server-url: "https://your-apm-server.com/api/v1/ingest/logs"
  api-key: "your-secret-api-key"
  masked-headers: "authorization,cookie,x-api-key"
  exclude-paths: "/health,/actuator/**,/metrics/**"
```

### application.properties

```properties
logdispatch.enabled=true
logdispatch.server-url=https://your-apm-server.com/api/v1/ingest/logs
logdispatch.api-key=your-secret-api-key
logdispatch.masked-headers=authorization,cookie,x-api-key
logdispatch.exclude-paths=/health,/actuator/**,/metrics/**
```

### Configuration Properties

| Property                     | Required | Description                                                                             |
| ---------------------------- | -------- | --------------------------------------------------------------------------------------- |
| `logdispatch.enabled`        | ❌ No     | Enables or disables the SDK. Defaults to `true`                                         |
| `logdispatch.server-url`     | ✅ Yes, when enabled | Full URL of the APM ingest endpoint                                          |
| `logdispatch.api-key`        | ✅ Yes, when enabled | API key used to authenticate with the APM server                             |
| `logdispatch.masked-headers` | ❌ No     | Comma-separated list of headers to mask. Defaults to none                               |
| `logdispatch.exclude-paths`  | ❌ No     | Comma-separated list of URI paths to exclude. Supports wildcards such as `/actuator/**` |
| `logdispatch.timeout-ms`     | ❌ No | Connection and read timeout in milliseconds. Defaults to `3000`.                            |

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

When a `@RestController` method throws an unhandled exception, or when a filter rejects a request (e.g., `403 Forbidden`, `404 Not Found`), the SDK:

1. Captures the request URI, HTTP method, exception class, message, and full stack trace.
2. Reads optional metadata from the `@LogDispatch` annotation.
3. Asynchronously sends a JSON payload to the configured `server-url`.
4. Includes the `X-API-KEY` header for authentication.
5. Logs a warning if the push fails and continues execution without affecting the application.

---

# What This SDK Sends

Every exception is pushed as a `POST` request to the configured `server-url`.

## Request Headers

| Header         | Value                          |
| -------------- | ------------------------------ |
| `Content-Type` | `application/json`             |
| `X-API-KEY`    | Value of `logdispatch.api-key` |

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
  }
}
```

## Payload Fields

| Field              | Type   | Description                                              |
| ------------------ | ------ | -------------------------------------------------------- |
| `timestamp`        | String | ISO-8601 UTC timestamp                                   |
| `errorType`        | String | Exception class name                                     |
| `statusCode`       | Number | HTTP status code                                         |
| `errorMessage`     | String | Exception message                                        |
| `errorPath`        | String | Request URI                                              |
| `affectedFeature`  | String | Controller name or annotation override                   |
| `affectedAPI`      | String | API path or annotation override                          |
| `apiType`          | String | HTTP method                                              |
| `affectedFunction` | String | Method name or annotation override                       |
| `stackTrace`       | String | Full stack trace                                         |
| `severity`         | String | WARNING, CRITICAL, or SECURITY                           |
| `inputInformation` | Object | Request metadata including headers, parameters, and body |

> **Note:** `inputInformation.body` is skipped for `multipart/form-data` uploads or payloads larger than 32 KB.

---

## Severity Mapping

| HTTP Status / Condition | Severity |
| ----------- | -------- |
| 4xx (Exception) | WARNING  |
| 5xx (Exception) | CRITICAL |
| Filter/Routing Error | SECURITY |

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

Any `2xx` response is treated as successful.

The SDK ignores the response body.

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

Without the annotation, the SDK defaults to:

* Controller class name
* Method name
* Raw request URI

---

# Testing & Contributing

If you want to contribute to this project, please follow our established automation testing best practices. 

Please see the [TESTING.md](TESTING.md) file for detailed guidelines on how to run, structure, and write tests for this SDK.

---

# Troubleshooting

For common problems and solutions, see:

```text
TROUBLESHOOTING.md
```

---

## Example App

A runnable Spring Boot demo is available in [example-app](./example-app). It includes dummy REST endpoints that intentionally throw exceptions so you can see LogDispatch capture and dispatch APM log payloads.

---

## License

MIT License
