# LogDispatch Spring Boot Starter

[![Maven Central](https://img.shields.io/maven-central/v/in.maheshlangote/logdispatch-spring-boot-starter)](https://central.sonatype.com/artifact/in.maheshlangote/logdispatch-spring-boot-starter)
[![CI](https://github.com/Mahesh-Langote/logdispatch/actions/workflows/logdispatch-pr-gate.yml/badge.svg?branch=main)](https://github.com/Mahesh-Langote/logdispatch/actions)
[![Java](https://img.shields.io/badge/Java-17%2B-blue?logo=openjdk)](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

A lightweight, zero-configuration **Application Performance Monitoring (APM) client** for Spring Boot.

It uses Spring AOP and Servlet Filters to automatically capture HTTP errors, latency profiling, response byte counts, system health, developer execution logs, request IP addresses (`requestIp`), and transaction correlation IDs (`traceId`), dispatching them asynchronously to your centralized APM server.

---

## Features

* **Zero Code Changes** — Works out of the box with no changes to your controllers or exception handlers.
* **Distributed Request Correlation** — Auto-generates unique `traceId` and `spanId` per transaction, injecting into SLF4J MDC (`[traceId=...]`) and setting `X-Trace-Id` HTTP response headers.
* **Multi-Threaded & Async Log Capture** — Uses `InheritableThreadLocal` and `traceId` context registry to capture 100% of logs across `@Async` worker threads and parallel execution pools.
* **Latency & Payload Size Metrics** — High-precision measurement of execution duration (`executionTimeMs`) and zero-RAM output byte streaming counters (`responseSizeBytes`).
* **Multi-Tagging System** — Auto-assigns system tags (`DEPRECATED_API`, `SLOW_REQUEST`, `HIGH_PAYLOAD_SIZE`, `SERVER_ERROR`, `CLIENT_ERROR`), annotation tags (`@LogDispatch(tags = {"..."})`), and dynamic runtime tags (`LogDispatchContext.addTag(...)`).
* **Ignore Control Annotation** — Selectively ignore specific controllers or methods using `@LogDispatch(enabled = false)`.
* **Configurable Request IP Capture** — Captures caller IP (`requestIp`), with option to mask (`logdispatch.include-request-ip=false`).
* **Configurable Dispatch Modes** — Choose between `errors-only`, `all` (100% APM traffic monitoring), or `errors-and-slow`.
* **System Health Snapshots** — Captures CPU usage % (`cpuUsagePercent`) and RAM Memory usage % (`memoryUsagePercent`) during request events.
* **Asynchronous & Resilient** — All log pushes run in a `CompletableFuture` background thread. Fails silently if the APM server is unreachable, so your application never crashes.

---

# Installation

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>in.maheshlangote</groupId>
    <artifactId>logdispatch-spring-boot-starter</artifactId>
    <version>1.2.0</version>
</dependency>
```

## Configuration

### application.yml

```yaml
logdispatch:
  enabled: true
  server-url: "https://your-apm-server.com/api/v1/ingest/logs"
  api-key: "your-secret-api-key"
  dispatch-mode: "errors-only" # 'errors-only', 'all', or 'errors-and-slow'
  slow-threshold-ms: 1000
  include-request-ip: true # Set to false to mask requestIp as "MASKED"
  timeout-ms: 3000
  max-stack-frames: 100
  masked-headers: "authorization,cookie,x-api-key"
  exclude-paths: "/health,/actuator/**,/metrics/**"
  logs:
    enabled: true
    min-level: "DEBUG"
    exclude-loggers:
      - "com.zaxxer.hikari"
```

### application.properties

```properties
logdispatch.enabled=true
logdispatch.server-url=https://your-apm-server.com/api/v1/ingest/logs
logdispatch.api-key=your-secret-api-key
logdispatch.dispatch-mode=errors-only
logdispatch.slow-threshold-ms=1000
logdispatch.include-request-ip=true
logdispatch.timeout-ms=3000
logdispatch.max-stack-frames=100
logdispatch.masked-headers=authorization,cookie,x-api-key
logdispatch.exclude-paths=/health,/actuator/**,/metrics/**
logdispatch.logs.enabled=true
logdispatch.logs.min-level=DEBUG
logdispatch.logs.exclude-loggers=com.zaxxer.hikari
```

### Configuration Properties

| Property | Required | Default | Description |
| :--- | :--- | :--- | :--- |
| `logdispatch.enabled` | ❌ No | `true` | Enables or disables the LogDispatch SDK. |
| `logdispatch.server-url` | ✅ Yes, when enabled | `http://localhost:8081/...` | Full URL of the APM ingest endpoint. |
| `logdispatch.api-key` | ✅ Yes, when enabled | `default-key` | API key used to authenticate with the APM server. |
| `logdispatch.dispatch-mode` | ❌ No | `all` | Controls when payloads are dispatched (`DispatchMode.ALL`, `DispatchMode.ERRORS_ONLY`, `DispatchMode.ERRORS_AND_SLOW`). |
| `logdispatch.slow-threshold-ms` | ❌ No | `1000` | Latency threshold in ms for tagging slow API requests (`SLOW_REQUEST`). |
| `logdispatch.include-request-ip` | ❌ No | `true` | Captures caller IP in `requestIp`. Set to `false` to mask IP as `"MASKED"`. |
| `logdispatch.timeout-ms` | ❌ No | `3000` | Connection and read timeout in milliseconds. |
| `logdispatch.max-stack-frames` | ❌ No | `100` | Maximum number of stack trace frames included per error payload. |
| `logdispatch.masked-headers` | ❌ No | `[]` | Comma-separated list of HTTP headers to mask (e.g. `authorization`). |
| `logdispatch.exclude-paths` | ❌ No | `[]` | Comma-separated list of URI paths to exclude (supports wildcards like `/actuator/**`). |
| `logdispatch.health.enabled` | ❌ No | `true` | Enables or disables registering the `/logdispatch/health` endpoint. |
| `logdispatch.logs.enabled` | ❌ No | `true` | Enables capturing developer execution debug logs during requests (buffered up to 128 KB). |
| `logdispatch.logs.min-level` | ❌ No | `DEBUG` | Minimum log level captured (`LogLevel.TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`). |

---

# What This SDK Sends

Every event is pushed as a `POST` request to the configured `server-url`.

## Request Headers

| Header | Value |
| :--- | :--- |
| `Content-Type` | `application/json` |
| `X-API-KEY` | Value of `logdispatch.api-key` |
| `X-LogDispatch-Version` | Dynamic SDK Version (e.g. `1.2.0`) |
| `X-LogDispatch-Language` | SDK Language (`java`) |

## Request Body Example

```json
{
  "timestamp": "2026-09-26T10:30:00.123Z",
  "traceId": "b3e39005-a5c8-4787-8f48-c3f8b5a70245",
  "spanId": "c4f5e6a7-1234-5678",
  "parentSpanId": null,
  "sdkVersion": "1.2.0",
  "sdkLanguage": "java",
  "requestIp": "103.21.12.44",

  "isError": false,
  "isDeprecated": true,
  "severity": "SUCCESS",
  "tags": [
    "CRITICAL_PAYMENT",
    "DEPRECATED_API",
    "SLOW_REQUEST"
  ],

  "statusCode": 200,
  "errorType": "SUCCESS",
  "errorMessage": "Request processed successfully.",
  "errorPath": "/api/v1/service-requests/statistics",
  "affectedFeature": "ServiceRequestControllerV1",
  "affectedAPI": "/api/v1/service-requests/statistics",
  "apiType": "GET",
  "affectedFunction": "loggedInServicePartnerProfileDetails",
  "stackTrace": "N/A",

  "executionTimeMs": 1420,
  "responseSizeBytes": 12850,

  "performanceBreakdown": null,
  "systemHealth": {
    "cpuUsagePercent": 18.5,
    "memoryUsagePercent": 64.2
  },

  "inputInformation": {
    "queryString": "status=ACTIVE",
    "parameters": {},
    "headers": {
      "host": "localhost:8080",
      "content-type": "application/json",
      "user-agent": "PostmanRuntime/7.32.3"
    },
    "body": "{\"entries\": []}"
  },
  "executionLogs": [
    "2026-09-26T10:30:00.010Z INFO in.maheshlangote.service : Fetched partner statistics"
  ]
}
```

## Payload Fields

| Field | Type | Description |
| :--- | :--- | :--- |
| `timestamp` | String | ISO-8601 UTC timestamp |
| `traceId` | String | Unique transaction correlation ID |
| `spanId` | String | Unique span ID for current execution step |
| `parentSpanId` | String | Parent caller span ID (if nested) |
| `sdkVersion` | String | SDK Version (e.g. `1.2.0`) |
| `sdkLanguage` | String | SDK Language (`java`) |
| `requestIp` | String | Remote caller IP address (`X-Forwarded-For` or remote addr, or `"MASKED"`) |
| `isError` | Boolean | `true` if HTTP status $\ge 400$, `false` if successful ($200\text{ OK}$) |
| `isDeprecated` | Boolean | `true` if method/class is annotated with `@Deprecated` |
| `severity` | String | `SUCCESS`, `WARNING`, `CRITICAL`, `SECURITY`, `ERROR`, `INFO` |
| `tags` | Array | System tags + custom annotation tags + dynamic runtime tags |
| `statusCode` | Number | HTTP status code (e.g. 200, 400, 403, 500) |
| `errorType` | String | Exception class name or `FilterError` / `SUCCESS` |
| `errorMessage` | String | Exception message or status description |
| `errorPath` | String | Request URI |
| `affectedFeature` | String | Controller name or `@LogDispatch` annotation override |
| `affectedAPI` | String | API path or `@LogDispatch` annotation override |
| `apiType` | String | HTTP method (`GET`, `POST`, etc.) or execution type |
| `affectedFunction` | String | Method name or `@LogDispatch` annotation override |
| `stackTrace` | String | Full stack trace string (or `"N/A"` for success) |
| `executionTimeMs` | Number | Total execution duration in milliseconds |
| `responseSizeBytes` | Number | Total output response payload size in bytes |
| `systemHealth` | Object | System health metrics (`cpuUsagePercent`, `memoryUsagePercent`) |
| `inputInformation` | Object | Request metadata including headers, parameters, and body (`inputInformation.body`) |
| `executionLogs` | Array | Structured array of developer logs printed during request execution |

---

# Custom Annotation, Custom Tags & Ignore Overrides

### 1. Custom Annotation Metadata & Tags (`@LogDispatch`)

Override default metadata (`feature`, `api`, `function`, `severity`, `tags`) with custom values:

```java
import in.maheshlangote.logdispatch.annotation.LogDispatch;
import in.maheshlangote.logdispatch.annotation.LogSeverity;

@RestController
@LogDispatch(feature = "Payment Gateway")
public class PaymentController {

    @PostMapping("/pay")
    @LogDispatch(
        api = "Process Payment",
        function = "handlePayment",
        severity = LogSeverity.CRITICAL,
        tags = {"CRITICAL_PAYMENT", "VIP_FLOW"} // 👈 Custom Annotation Tags
    )
    public void handlePayment() {
        // ...
    }
}
```

### 2. Ignoring Specific Controllers or Methods (`@LogDispatch(enabled = false)`)

To completely ignore and skip APM telemetry for a specific controller or method:

```java
@RestController
@LogDispatch(enabled = false) // 👈 Ignores all APIs in this controller completely
public class InternalAdminController {

    @GetMapping("/ping")
    @LogDispatch(enabled = false) // 👈 Ignores this specific endpoint
    public String ping() {
        return "pong";
    }
}
```

### 3. Dynamic Runtime Tags (`LogDispatchContext`)

Add tags dynamically in code during method execution based on runtime business logic:

```java
import in.maheshlangote.logdispatch.LogDispatchContext;

@Service
public class OrderService {

    public void processOrder(Order order) {
        if (order.getAmount() > 10000) {
            LogDispatchContext.addTag("HIGH_VALUE_ORDER"); // 👈 Dynamic Runtime Tag
        }
    }
}
```

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

---

## License

MIT License
