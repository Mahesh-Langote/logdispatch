package in.maheshlangote.logdispatch;

import in.maheshlangote.logdispatch.config.DispatchMode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Filter that captures HTTP request telemetry, errors, and performance metrics
 * and dispatches them asynchronously to the centralized APM server.
 */
public class LogDispatchFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LogDispatchFilter.class);

    private final String serverUrl;
    private final String apiKey;
    private final RestTemplate restTemplate;
    private final boolean enabled;
    private final int timeoutMs;
    private final Set<String> maskedHeaders;
    private final List<String> excludePaths;
    private final Executor dispatchExecutor;
    private final int maxStackFrames;
    private final DispatchMode dispatchMode;
    private final int slowThresholdMs;
    private final boolean includeRequestIp;

    private static final AntPathMatcher ANT_PATH_MATCHER = new AntPathMatcher();
    private static final int DEFAULT_MAX_STACK_FRAMES = 100;
    private static final int MAX_PAYLOAD_SIZE = 128 * 1024; // 128 KB
    private static final long LARGE_PAYLOAD_THRESHOLD = 500 * 1024; // 500 KB

    public LogDispatchFilter(String serverUrl, String apiKey, List<String> maskedHeaders, List<String> excludePaths, int timeoutMs) {
        this(true, serverUrl, apiKey, maskedHeaders, excludePaths, timeoutMs, DEFAULT_MAX_STACK_FRAMES);
    }

    public LogDispatchFilter(boolean enabled, String serverUrl, String apiKey, List<String> maskedHeaders,
            List<String> excludePaths, int timeoutMs) {
        this(enabled, serverUrl, apiKey, maskedHeaders, excludePaths, timeoutMs, DEFAULT_MAX_STACK_FRAMES);
    }

    public LogDispatchFilter(boolean enabled, String serverUrl, String apiKey, List<String> maskedHeaders,
            List<String> excludePaths, int timeoutMs, int maxStackFrames) {
        this(enabled, serverUrl, apiKey, maskedHeaders, excludePaths, new RestTemplate(), null, timeoutMs,
                maxStackFrames, DispatchMode.ALL, 1000, true);
    }

    public LogDispatchFilter(boolean enabled, String serverUrl, String apiKey, List<String> maskedHeaders,
            List<String> excludePaths, RestTemplate restTemplate, Executor dispatchExecutor, int timeoutMs,
            int maxStackFrames) {
        this(enabled, serverUrl, apiKey, maskedHeaders, excludePaths, restTemplate, dispatchExecutor, timeoutMs,
                maxStackFrames, DispatchMode.ALL, 1000, true);
    }

    public LogDispatchFilter(boolean enabled, String serverUrl, String apiKey, List<String> maskedHeaders,
            List<String> excludePaths, RestTemplate restTemplate, Executor dispatchExecutor, int timeoutMs,
            int maxStackFrames, String dispatchMode, int slowThresholdMs, boolean includeRequestIp) {
        this(enabled, serverUrl, apiKey, maskedHeaders, excludePaths, restTemplate, dispatchExecutor, timeoutMs,
                maxStackFrames, DispatchMode.fromValue(dispatchMode), slowThresholdMs, includeRequestIp);
    }

    public LogDispatchFilter(boolean enabled, String serverUrl, String apiKey, List<String> maskedHeaders,
            List<String> excludePaths, RestTemplate restTemplate, Executor dispatchExecutor, int timeoutMs,
            int maxStackFrames, DispatchMode dispatchMode, int slowThresholdMs, boolean includeRequestIp) {
        this.enabled = enabled;
        this.serverUrl = serverUrl;
        this.apiKey = apiKey;
        this.timeoutMs = (timeoutMs > 0) ? timeoutMs : 3000;
        this.maxStackFrames = (maxStackFrames > 0) ? maxStackFrames : DEFAULT_MAX_STACK_FRAMES;
        this.dispatchExecutor = dispatchExecutor;
        this.dispatchMode = (dispatchMode != null) ? dispatchMode : DispatchMode.ALL;
        this.slowThresholdMs = (slowThresholdMs > 0) ? slowThresholdMs : 1000;
        this.includeRequestIp = includeRequestIp;
        this.restTemplate = (restTemplate != null) ? restTemplate : new RestTemplate();

        if (this.restTemplate.getRequestFactory() instanceof SimpleClientHttpRequestFactory factory) {
            factory.setConnectTimeout(this.timeoutMs);
            factory.setReadTimeout(this.timeoutMs);
        } else {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(this.timeoutMs);
            factory.setReadTimeout(this.timeoutMs);
            this.restTemplate.setRequestFactory(factory);
        }

        this.maskedHeaders = maskedHeaders == null ? Set.of() : maskedHeaders.stream()
                .filter(h -> h != null && !h.trim().isEmpty())
                .map(h -> h.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        this.excludePaths = excludePaths == null ? List.of() : excludePaths.stream()
                .filter(p -> p != null && !p.trim().isEmpty())
                .map(String::trim)
                .collect(Collectors.toList());
    }

    LogDispatchFilter(String serverUrl, String apiKey, List<String> maskedHeaders, List<String> excludePaths,
                      RestTemplate restTemplate, Executor dispatchExecutor, int timeoutMs) {
        this(true, serverUrl, apiKey, maskedHeaders, excludePaths, restTemplate, dispatchExecutor, timeoutMs,
                DEFAULT_MAX_STACK_FRAMES, DispatchMode.ALL, 1000, true);
    }

    LogDispatchFilter(boolean enabled, String serverUrl, String apiKey, List<String> maskedHeaders,
            List<String> excludePaths, RestTemplate restTemplate, Executor dispatchExecutor, int timeoutMs) {
        this(enabled, serverUrl, apiKey, maskedHeaders, excludePaths, restTemplate, dispatchExecutor, timeoutMs,
                DEFAULT_MAX_STACK_FRAMES, DispatchMode.ALL, 1000, true);
    }

    private boolean isPathExcluded(String requestPath) {
        if (excludePaths.isEmpty()) {
            return false;
        }
        for (String excludePattern : excludePaths) {
            if (ANT_PATH_MATCHER.match(excludePattern, requestPath)) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldWrapRequest(HttpServletRequest request) {
        if ("GET".equalsIgnoreCase(request.getMethod()) && request.getContentLength() <= 0) {
            return false;
        }
        String contentType = request.getContentType();
        if (contentType != null && contentType.toLowerCase().startsWith("multipart/")) {
            return false;
        }
        int contentLength = request.getContentLength();
        return contentLength <= MAX_PAYLOAD_SIZE;
    }

    private String extractRequestIp(HttpServletRequest request) {
        if (!includeRequestIp) {
            return "MASKED";
        }
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String requestPath = request.getRequestURI();
        if (isPathExcluded(requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Trace and Span correlation IDs
        String traceId = getOrGenerateHeader(request, "X-Trace-Id", "X-Request-Id");
        String parentSpanId = request.getHeader("X-Span-Id");
        String spanId = UUID.randomUUID().toString();

        LogDispatchContext.setTraceId(traceId);
        LogDispatchContext.setSpanId(spanId);
        if (parentSpanId != null) {
            LogDispatchContext.setParentSpanId(parentSpanId);
        }

        MDC.put("traceId", traceId);
        MDC.put("spanId", spanId);
        response.setHeader("X-Trace-Id", traceId);

        HttpServletRequest requestToUse = request;
        if (shouldWrapRequest(request)) {
            requestToUse = new ContentCachingRequestWrapper(request);
        }

        ByteCountingResponseWrapper responseToUse = new ByteCountingResponseWrapper(response);

        long startTime = System.currentTimeMillis();
        Throwable unhandledException = null;

        try {
            LogDispatchLogBuffer.init(traceId);
            filterChain.doFilter(requestToUse, responseToUse);
        } catch (Exception ex) {
            unhandledException = ex;
            throw ex;
        } finally {
            long executionTimeMs = System.currentTimeMillis() - startTime;
            long responseSizeBytes = responseToUse.getByteCount();

            try {
                Boolean isIgnored = (Boolean) requestToUse.getAttribute("logdispatch.ignored");
                if (!Boolean.TRUE.equals(isIgnored)) {
                    int status = responseToUse.getStatus();
                    if (unhandledException != null) {
                        status = 500;
                    }

                    boolean isError = (status >= 400);
                    boolean isSlow = executionTimeMs >= slowThresholdMs;

                    boolean shouldDispatch = shouldDispatchPayload(isError, isSlow);

                    if (shouldDispatch) {
                        String requestIp = extractRequestIp(requestToUse);
                        Map<String, Object> inputInfo = extractInputInformation(requestToUse);
                        List<String> executionLogs = LogDispatchLogBuffer.getLogs();

                        Throwable aspectEx = (Throwable) requestToUse.getAttribute("logdispatch.exception");
                        Throwable actualEx = unhandledException != null ? unhandledException : aspectEx;

                        Boolean isDeprecatedAttr = (Boolean) requestToUse.getAttribute("logdispatch.isDeprecated");
                        boolean isDeprecated = Boolean.TRUE.equals(isDeprecatedAttr);

                        List<String> mergedTags = collectTags(requestToUse, isError, isSlow, isDeprecated, status, responseSizeBytes);

                        if (actualEx != null) {
                            String feature = (String) requestToUse.getAttribute("logdispatch.feature");
                            String api = (String) requestToUse.getAttribute("logdispatch.api");
                            String function = (String) requestToUse.getAttribute("logdispatch.function");

                            if (feature == null) feature = actualEx.getClass().getSimpleName();
                            if (api == null) api = requestToUse.getRequestURI();
                            if (function == null) function = "UNKNOWN";

                            pushTelemetryAsync(requestToUse, traceId, spanId, parentSpanId, requestIp, isError, isDeprecated, status,
                                    actualEx.getClass().getSimpleName(), actualEx.getMessage(), formatStackTrace(actualEx),
                                    feature, api, function, executionTimeMs, responseSizeBytes, mergedTags, inputInfo, executionLogs);
                        } else if (isError) {
                            pushFilterErrorAsync(requestToUse, traceId, spanId, parentSpanId, requestIp, isDeprecated, status,
                                    executionTimeMs, responseSizeBytes, mergedTags, inputInfo, executionLogs);
                        } else {
                            // Successful execution telemetry (2xx OK)
                            String feature = (String) requestToUse.getAttribute("logdispatch.feature");
                            String api = (String) requestToUse.getAttribute("logdispatch.api");
                            String function = (String) requestToUse.getAttribute("logdispatch.function");

                            if (feature == null) feature = "Controller";
                            if (api == null) api = requestToUse.getRequestURI();
                            if (function == null) function = "handleRequest";

                            pushSuccessAsync(requestToUse, traceId, spanId, parentSpanId, requestIp, isDeprecated, status,
                                    feature, api, function, executionTimeMs, responseSizeBytes, mergedTags, inputInfo, executionLogs);
                        }
                    }
                }
            } finally {
                LogDispatchLogBuffer.clear();
                LogDispatchContext.clear();
                MDC.remove("traceId");
                MDC.remove("spanId");
            }
        }
    }

    private boolean shouldDispatchPayload(boolean isError, boolean isSlow) {
        if (dispatchMode == DispatchMode.ERRORS_ONLY) {
            return isError;
        }
        if (dispatchMode == DispatchMode.ERRORS_AND_SLOW) {
            return isError || isSlow;
        }
        return true; // DispatchMode.ALL
    }

    private String getOrGenerateHeader(HttpServletRequest request, String... headerNames) {
        for (String name : headerNames) {
            String val = request.getHeader(name);
            if (val != null && !val.isBlank()) {
                return val.trim();
            }
        }
        return UUID.randomUUID().toString();
    }

    private List<String> collectTags(HttpServletRequest request, boolean isError, boolean isSlow, boolean isDeprecated,
                                     int statusCode, long responseSizeBytes) {
        List<String> tags = new ArrayList<>();

        if (isDeprecated) tags.add("DEPRECATED_API");
        if (isSlow) tags.add("SLOW_REQUEST");
        if (responseSizeBytes >= LARGE_PAYLOAD_THRESHOLD) tags.add("HIGH_PAYLOAD_SIZE");
        if (statusCode >= 500) tags.add("SERVER_ERROR");
        else if (statusCode >= 400) tags.add("CLIENT_ERROR");

        @SuppressWarnings("unchecked")
        List<String> annotationTags = (List<String>) request.getAttribute("logdispatch.tags");
        if (annotationTags != null) {
            tags.addAll(annotationTags);
        }

        List<String> runtimeTags = LogDispatchContext.getTags();
        if (runtimeTags != null && !runtimeTags.isEmpty()) {
            tags.addAll(runtimeTags);
        }

        return tags.stream().distinct().collect(Collectors.toList());
    }

    private Map<String, Object> extractSystemHealth() {
        Map<String, Object> health = new HashMap<>();
        try {
            OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            double cpu = osBean.getProcessCpuLoad() * 100.0;
            health.put("cpuUsagePercent", Double.isNaN(cpu) || cpu < 0 ? 0.0 : Math.round(cpu * 10.0) / 10.0);
        } catch (Exception e) {
            health.put("cpuUsagePercent", 0.0);
        }

        try {
            Runtime runtime = Runtime.getRuntime();
            long max = runtime.maxMemory();
            long total = runtime.totalMemory();
            long free = runtime.freeMemory();
            long used = total - free;
            double mem = ((double) used / max) * 100.0;
            health.put("memoryUsagePercent", Math.round(mem * 10.0) / 10.0);
        } catch (Exception e) {
            health.put("memoryUsagePercent", 0.0);
        }

        return health;
    }

    private void pushSuccessAsync(HttpServletRequest request, String traceId, String spanId, String parentSpanId,
                                  String requestIp, boolean isDeprecated, int statusCode, String feature, String api, String function,
                                  long executionTimeMs, long responseSizeBytes, List<String> tags,
                                  Map<String, Object> inputInfo, List<String> executionLogs) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        dispatchAsync(() -> {
            try {
                LogDispatchPayload payload = new LogDispatchPayload(
                        Instant.now().toString(),
                        traceId,
                        spanId,
                        parentSpanId,
                        LogDispatchVersion.getSdkVersion(),
                        LogDispatchVersion.SDK_LANGUAGE,
                        requestIp,
                        false,
                        isDeprecated,
                        "SUCCESS",
                        tags,
                        statusCode,
                        "SUCCESS",
                        "Request processed successfully.",
                        path,
                        feature,
                        api,
                        method,
                        function,
                        "N/A",
                        executionTimeMs,
                        responseSizeBytes,
                        null,
                        extractSystemHealth(),
                        inputInfo,
                        executionLogs
                );

                sendPayload(payload);
            } catch (Exception e) {
                log.warn("[LogDispatch] Failed to push success telemetry: {}", e.getMessage());
            }
        });
    }

    private void pushFilterErrorAsync(HttpServletRequest request, String traceId, String spanId, String parentSpanId,
                                       String requestIp, boolean isDeprecated, int statusCode, long executionTimeMs,
                                       long responseSizeBytes, List<String> tags, Map<String, Object> inputInfo, List<String> executionLogs) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        dispatchAsync(() -> {
            try {
                LogDispatchPayload payload = new LogDispatchPayload(
                        Instant.now().toString(),
                        traceId,
                        spanId,
                        parentSpanId,
                        LogDispatchVersion.getSdkVersion(),
                        LogDispatchVersion.SDK_LANGUAGE,
                        requestIp,
                        true,
                        isDeprecated,
                        "SECURITY",
                        tags,
                        statusCode,
                        "FilterError",
                        "Request failed with status " + statusCode + " at filter level.",
                        path,
                        "FilterSecurity/Routing",
                        path,
                        method,
                        "doFilter",
                        "No stack trace available for filter-level status codes.",
                        executionTimeMs,
                        responseSizeBytes,
                        null,
                        extractSystemHealth(),
                        inputInfo,
                        executionLogs
                );

                sendPayload(payload);
            } catch (Exception e) {
                log.warn("[LogDispatch] Failed to push filter error: {}", e.getMessage());
            }
        });
    }

    private void pushTelemetryAsync(HttpServletRequest request, String traceId, String spanId, String parentSpanId,
                                    String requestIp, boolean isError, boolean isDeprecated, int statusCode, String errorType,
                                    String errorMessage, String stackTrace, String feature, String api, String function,
                                    long executionTimeMs, long responseSizeBytes, List<String> tags,
                                    Map<String, Object> inputInfo, List<String> executionLogs) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        dispatchAsync(() -> {
            try {
                String customSeverity = (String) request.getAttribute("logdispatch.severity");
                String severity = (customSeverity != null && !customSeverity.isEmpty())
                        ? customSeverity
                        : (isError ? ((statusCode >= 500) ? "CRITICAL" : "WARNING") : "SUCCESS");

                LogDispatchPayload payload = new LogDispatchPayload(
                        Instant.now().toString(),
                        traceId,
                        spanId,
                        parentSpanId,
                        LogDispatchVersion.getSdkVersion(),
                        LogDispatchVersion.SDK_LANGUAGE,
                        requestIp,
                        isError,
                        isDeprecated,
                        severity,
                        tags,
                        statusCode,
                        errorType,
                        errorMessage,
                        path,
                        feature,
                        api,
                        method,
                        function,
                        stackTrace,
                        executionTimeMs,
                        responseSizeBytes,
                        null,
                        extractSystemHealth(),
                        inputInfo,
                        executionLogs
                );

                sendPayload(payload);
            } catch (Exception e) {
                log.warn("[LogDispatch] Failed to push telemetry: {}", e.getMessage());
            }
        });
    }

    private void sendPayload(LogDispatchPayload payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-KEY", apiKey);
        headers.set("X-LogDispatch-Version", LogDispatchVersion.getSdkVersion());
        headers.set("X-LogDispatch-Language", LogDispatchVersion.SDK_LANGUAGE);

        HttpEntity<LogDispatchPayload> entity = new HttpEntity<>(payload, headers);
        restTemplate.postForEntity(serverUrl, entity, String.class);
    }

    private String formatStackTrace(Throwable exception) {
        StackTraceElement[] frames = exception.getStackTrace();
        int includedFrames = Math.min(frames.length, maxStackFrames);
        StringBuilder stackTrace = new StringBuilder();
        for (int i = 0; i < includedFrames; i++) {
            stackTrace.append(frames[i]).append("\n");
        }
        if (frames.length > includedFrames) {
            stackTrace.append("... ")
                    .append(frames.length - includedFrames)
                    .append(" more frames truncated\n");
        }
        return stackTrace.toString();
    }

    private void dispatchAsync(Runnable task) {
        if (dispatchExecutor == null) {
            CompletableFuture.runAsync(task);
        } else {
            CompletableFuture.runAsync(task, dispatchExecutor);
        }
    }

    private Map<String, Object> extractInputInformation(HttpServletRequest request) {
        Map<String, Object> inputInfo = new HashMap<>();

        inputInfo.put("queryString", request.getQueryString());

        Map<String, String[]> parameterMap = request.getParameterMap();
        if (parameterMap != null && !parameterMap.isEmpty()) {
            inputInfo.put("parameters", new HashMap<>(parameterMap));
        }

        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                String value = maskedHeaders.contains(headerName.toLowerCase(Locale.ROOT)) ? "********" : request.getHeader(headerName);
                headers.put(headerName, value);
            }
        }
        inputInfo.put("headers", headers);

        if (request instanceof ContentCachingRequestWrapper wrapper) {
            byte[] buf = wrapper.getContentAsByteArray();
            if (buf.length > 0) {
                try {
                    String encoding = wrapper.getCharacterEncoding();
                    if (encoding == null) encoding = "UTF-8";
                    String body = new String(buf, 0, Math.min(buf.length, 10000), encoding);
                    inputInfo.put("body", body);
                } catch (Exception e) {
                    inputInfo.put("body", "[Error reading request body]");
                }
            }
        }

        return inputInfo;
    }
}
