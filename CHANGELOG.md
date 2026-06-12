# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
