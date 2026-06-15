# Troubleshooting

Frequently asked questions and common issues when using LogDispatch.

---

## Why are no errors appearing on my APM dashboard?

**Possible causes and fixes:**

1. **SDK is disabled:** Check that `logdispatch.enabled` is not set to `false`. The default is `true`.
2. **Missing or incorrect configuration:** Verify `logdispatch.server-url` and `logdispatch.api-key` are set and point to your actual APM server.
3. **No exceptions thrown:** LogDispatch only captures unhandled exceptions from `@RestController` classes and 4xx/5xx filter-level errors. Controlled responses (e.g., `ResponseEntity.status(400).body(...)` without throwing) are not intercepted.
4. **Network issues:** Check your application logs for `WARN [LogDispatch]` entries. The SDK logs connection failures silently — check for messages like `Connection refused` or `401 UNAUTHORIZED`.

```yml
# Verify your configuration:
logdispatch:
  enabled: true
  server-url: "https://your-apm-server.com/api/v1/ingest/logs"
  api-key: "your-secret-api-key"
```

---

## Why is the `/logdispatch/health` endpoint returning 429?

The health endpoint has a built-in rate limit of **60 requests per minute per IP address**. If your APM server polls more frequently, it will receive `429 Too Many Requests` responses.

The rate limit uses a 60-second sliding window per client IP. If your monitoring needs a higher frequency, you can:

- Reduce the polling interval to once every second (60 req/min max).
- Have multiple clients spread across different IPs.
- Disable the health endpoint by setting `logdispatch.enabled: false` and implement your own.

---

## Why are sensitive headers still visible in the APM payload?

The `logdispatch.masked-headers` property masks header values in the `inputInformation.headers` section of the payload. Check the following:

1. **Header names must be lowercase:** The masking is case-insensitive, but the list uses lowercase internally. Use `authorization` not `Authorization`.
2. **Comma-separated format:** Each header name should be separated by a comma. Leading/trailing spaces are trimmed.

```yml
# Correct format for masked headers:
logdispatch:
  masked-headers: "authorization,cookie,x-api-key,set-cookie"
```

3. **Verify in application logs:** The masked headers will appear as `********` in the payload sent to the APM server. No additional code changes are needed.

---

## Why is the SDK dispatching on paths I have excluded?

The `logdispatch.exclude-paths` property uses [Ant-style path patterns](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/util/AntPathMatcher.html) and is matched against the request URI.

Common mistakes:

1. **Pattern syntax:** Patterns like `/health` match exactly that path. Wildcard patterns like `/actuator/**` match all sub-paths.
2. **Comma-separated format:** Paths must be comma-separated. Whitespace around commas is trimmed.
3. **Case sensitivity:** URIs are matched case-sensitively.

```yml
# Correct format for exclude paths:
logdispatch:
  exclude-paths: "/health,/actuator/**,/metrics/**,/swagger-ui/**"
```

> **Note:** The `/logdispatch/health` endpoint is automatically excluded and does not need to be added to `exclude-paths`.

---

## Why is the SDK sending logs in my local or test environment?

The SDK is active by default. To disable it in non-production environments, use Spring profiles:

```yml
# application-dev.yml
logdispatch:
  enabled: false
```

Or conditionally set it with a Spring expression:

```yml
# application.yml
logdispatch:
  enabled: ${LOG_DISPATCH_ENABLED:true}
  server-url: "${LOG_DISPATCH_SERVER_URL:}"
  api-key: "${LOG_DISPATCH_API_KEY:}"
```

When `logdispatch.enabled` is set to `false`, the AOP aspect, filter, and health endpoint are all disabled — no network calls will be made.

---

## Why are there duplicate log entries on my APM dashboard?

Duplicate entries typically occur in one of these scenarios:

1. **Multiple instances of LogDispatch:** If your application has multiple LogDispatch configurations (e.g., manual bean registration alongside auto-configuration), the filter may be registered more than once.

2. **Exception re-throwing:** If a global `@ControllerAdvice` catches an exception and re-throws it, or returns a 4xx/5xx status, the filter may capture it a second time.

3. **Filter and Aspect overlap:** The `LogDispatchAspect` captures exceptions from `@RestController` methods, stores them in request attributes, and the `LogDispatchFilter` picks them up in its `finally` block. This produces a single dispatch per error — but if your security filter or custom filter also triggers an error response, both may be recorded.

**To diagnose:** Check your application logs for duplicate `WARN [LogDispatch]` entries and verify the `errorPath` and `timestamp` fields in the duplicate payloads to trace the origin.
