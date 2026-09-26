# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.2.2] - 2026-09-26
### Added
- **Type-Safe `DispatchMode` & `LogLevel` Enums**: Introduced strongly typed `DispatchMode` (`ALL`, `ERRORS_ONLY`, `ERRORS_AND_SLOW`) and `LogLevel` (`TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`) enums with Spring Boot relaxed binding and Jackson serialization.

### Fixed
- **Tomcat Servlet Request Facade Recycling Fix**: Extracted HTTP request attributes (`path`, `method`, `customSeverity`) synchronously on the request handling thread inside `doFilterInternal()` before async dispatching, completely preventing Tomcat facade recycling exceptions (`The request object has been recycled and is no longer associated with this facade`) in background threads under high concurrency.
- **Default APM Dispatch Mode**: Defaulted `dispatch-mode` to `DispatchMode.ALL` to capture 100% of HTTP transactions unless explicitly configured otherwise by developers.

## [1.2.0] - 2026-09-26
### Added
- **Distributed Request & Span Correlation**: Added `traceId`, `spanId`, and `parentSpanId` correlation fields. Injects `traceId` and `spanId` into SLF4J MDC (`[traceId=...]`) and returns `X-Trace-Id` HTTP response header for frontend-to-backend correlation.
- **Client Request IP Tracking (`requestIp`)**: Added `requestIp` to payloads, extracted from `X-Forwarded-For` or `request.getRemoteAddr()`. Configurable via `logdispatch.include-request-ip` (default: `true`, set to `false` to mask `requestIp` as `"MASKED"`).
- **Selective Ignore Annotation Control**: Added `enabled` parameter to `@LogDispatch(enabled = false)` allowing developers to completely ignore and skip log dispatching for specific controllers or methods.
- **Latency & Performance Profiling**: Added `executionTimeMs` measuring high-precision response time for HTTP requests and non-HTTP background executions.
- **Zero-RAM Response Byte Counter**: Created `ByteCountingResponseWrapper` to track output streaming payload size (`responseSizeBytes`) with 0 extra MB RAM overhead.
- **Streamlined Status & Severity**: Added explicit 1-bit `isError` boolean flag (`true`/`false`) and `LogSeverity.SUCCESS` for 200 OK executions.
- **Java `@Deprecated` Auto-Detection**: Added `isDeprecated` boolean flag using Reflection to detect Java `@Deprecated` annotations on controllers and methods.
- **Multi-Tag System (`tags`)**: Supports auto-assigned system tags (`DEPRECATED_API`, `SLOW_REQUEST`, `HIGH_PAYLOAD_SIZE`, `SERVER_ERROR`, `CLIENT_ERROR`), custom annotation tags via `@LogDispatch(tags = {"..."})`, and dynamic runtime tags via `LogDispatchContext.addTag(...)`.
- **Type-Safe `DispatchMode` & `LogLevel` Enums**: Replaced raw configuration strings with type-safe `DispatchMode` (`ALL`, `ERRORS_ONLY`, `ERRORS_AND_SLOW`) and `LogLevel` (`TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`) enums with Jackson serialization and Spring relaxed binding support.
- **Configurable Dispatch Modes**: Added `logdispatch.dispatch-mode` (default: `DispatchMode.ALL` for 100% APM capture) and `logdispatch.slow-threshold-ms` (default: `1000`).
- **Dynamic SDK Version & Language Telemetry**: Added `sdkVersion` (dynamically sourced from JAR Manifest with fallback to `0.0.0`) and `sdkLanguage` (`"java"`), shipped in payload and `X-LogDispatch-Version` / `X-LogDispatch-Language` HTTP headers.
- **Runtime System Health Snapshot**: Included `systemHealth` map capturing CPU usage % (`cpuUsagePercent`) and Memory heap usage % (`memoryUsagePercent`).
- **100% Multi-Threaded & Async Log Capture**: Upgraded `LogDispatchLogBuffer` to use `InheritableThreadLocal` + `traceId`-bound registry map (`TRACE_LOG_BUFFERS`), capturing logs from `@Async` methods, `CompletableFuture`, and worker thread pools without dropping console logs.

## [1.1.1] - 2026-09-10
### Added
- Added type-safe `LogSeverity` enum (`DEFAULT`, `DEBUG`, `INFO`, `WARNING`, `ERROR`, `CRITICAL`, `SECURITY`, `FATAL`) for explicitly overriding severity levels per controller or method via `@LogDispatch(severity = LogSeverity.CRITICAL)`.
- Updated `LogDispatchAspect` and `LogDispatchFilter` to capture and apply custom severity overrides from `@LogDispatch`.
- Added standard practice guidelines for choosing severity levels in `LogSeverity` Javadoc and `README.md`.

## [1.1.0] - 2026-09-06
### Added
- Formatted `executionLogs` as pure console-style log strings (`${timestamp} ${level} ${loggerName} : ${message}`), eliminating `threadName`, redundant JSON metadata, and null fields (Option B).
- Implemented an internal 128 KB execution log buffer limit (`MAX_BUFFER_BYTES`), capturing all request logs without arbitrary 50-entry count caps while preventing database truncation.
- Increased `MAX_PAYLOAD_SIZE` for request body caching to 128 KB.

## [1.0.9] - 2026-09-06
### Added
- Added `logdispatch.logs.exclude-loggers` configuration property to filter out noisy infrastructure loggers (e.g. `com.zaxxer.hikari`) from request execution logs.
- Added `logdispatch.logs.include-loggers` configuration property to optionally capture logs exclusively from matching logger package prefixes.
- Resilient `min-level` parsing to gracefully support comma-separated level declarations (e.g. `DEBUG, INFO, WARN, ERROR`).

## [1.0.8] - 2026-09-06
### Added
- Added developer execution debug log capturing (`executionLogs`) per-request thread via SLF4J/Logback integration (`DEBUG`, `INFO`, `WARN`, `ERROR`).
- Added `logdispatch.logs.enabled`, `logdispatch.logs.max-entries`, and `logdispatch.logs.min-level` configuration properties.
- Added `logdispatch.health.enabled` configuration property to allow opting out of registering the `/logdispatch/health` endpoint (PR #46).
- Added `logdispatch.enabled` master toggle property (PR #40).
- Added configurable connection and read timeouts (`timeoutMs`) via `logdispatch.timeout-ms` (PR #38).
- Added `example-app` Spring Boot reference implementation (PR #39).

## [1.0.7] - 2026-06-20
### Added
- Implemented a new `SECURITY` severity classification for unhandled filter-level exceptions.
- Added strict type safety to the dispatch payload structure via `LogDispatchPayload`.
- Added support for excluding specific URI paths from being intercepted by the filter.
- Added support for tracking and masking additional HTTP headers in `LogDispatchFilter`.
- Added `LogDispatchAspectTest` for full AOP test coverage.

### Changed
- Refactored `LogDispatchFilterTest` into focused, SRP-compliant test files with a shared base test class.
- Simplified GitHub Actions CI/CD workflows and implemented a PR gating mechanism.
- Bumped `spring.boot.version` dependency from `3.3.2` to `3.5.15`.

### Documentation
- Added explicit testing guidelines and contribution instructions via `TESTING.md`.
- Added a comprehensive troubleshooting guide (`TROUBLESHOOTING.md`).
- Added `application.properties` configuration examples.
- Removed deprecated references to `logdispatch.enabled`.
- Added CI, Java, and License status badges to `README.md`.

## [1.0.6] - 2026-06-12
### Changed
- Upgraded internal build infrastructure: Maven wrapper bumped to 3.9.8 and `maven-compiler-plugin` to 3.15.0.

## [1.0.5] - 2026-06-12
### Changed
- Downgraded target Java compiler version from 21 to 17 to maximize compatibility for Spring Boot 3 projects.
- Migrated CI/CD pipeline to GitHub Actions with matrix testing across JDK 17 and 21.

## [1.0.4] - 2026-06-12
### Added
- Added `inputInformation` to the APM log payload, capturing the request's query string, parameters, headers, and body.
- Added strict memory protections: Request bodies are not cached if the `Content-Type` is `multipart/form-data` or if the `Content-Length` exceeds 32 KB.

## [1.0.3] - 2026-05-31
### Fixed
- Fixed bug where Spring `@ControllerAdvice` handled exceptions were incorrectly reported to the APM server as 500 Internal Server Error. The APM client now correctly respects the final HTTP response status (e.g. 400 Bad Request, 403 Forbidden) set by the global exception handler.
- Fixed `maven-javadoc-plugin` build warnings.

## [1.0.2] - 2026-05-30
### Added
- Added `GET /logdispatch/health` public endpoint for APM servers to monitor application uptime.
- Added built-in strict rate limiting (60 req/min) to the health endpoint.
- Added `LogDispatchFilter` to intercept filter-level errors (like 403 Forbidden) that bypass Spring RestControllers.
- Added `apiType` (HTTP Method) to JSON payloads.
### Changed
- Improved auto-configuration to automatically register the servlet filter with highest precedence.

## [1.0.0] - Initial Release
### Added
- Core asynchronous APM log dispatcher.
- Spring AOP `@LogDispatch` annotation support.
