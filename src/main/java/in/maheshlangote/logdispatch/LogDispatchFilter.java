package in.maheshlangote.logdispatch;

import in.maheshlangote.logdispatch.annotation.LogSeverity;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
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
    private final List<String> excludeMethods;
    private final boolean ignoreOptionsRequests;
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
                maxStackFrames, DispatchMode.ERRORS_AND_SLOW, 3000, true);
    }

    public LogDispatchFilter(boolean enabled, String serverUrl, String apiKey, List<String> maskedHeaders,
            List<String> excludePaths, RestTemplate restTemplate, Executor dispatchExecutor, int timeoutMs,
            int maxStackFrames) {
        this(enabled, serverUrl, apiKey, maskedHeaders, excludePaths, restTemplate, dispatchExecutor, timeoutMs,
                maxStackFrames, DispatchMode.ERRORS_AND_SLOW, 3000, true);
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
        this(enabled, serverUrl, apiKey, maskedHeaders, excludePaths, List.of("OPTIONS"), true, restTemplate,
                dispatchExecutor, timeoutMs, maxStackFrames, dispatchMode, slowThresholdMs, includeRequestIp);
    }

    public LogDispatchFilter(boolean enabled, String serverUrl, String apiKey, List<String> maskedHeaders,
            List<String> excludePaths, List<String> excludeMethods, boolean ignoreOptionsRequests,
            RestTemplate restTemplate, Executor dispatchExecutor, int timeoutMs, int maxStackFrames,
            DispatchMode dispatchMode, int slowThresholdMs, boolean includeRequestIp) {
        this.enabled = enabled;
        this.serverUrl = serverUrl;
        this.apiKey = apiKey;
        this.timeoutMs = (timeoutMs > 0) ? timeoutMs : 3000;
        this.maxStackFrames = (maxStackFrames > 0) ? maxStackFrames : DEFAULT_MAX_STACK_FRAMES;
        this.dispatchExecutor = dispatchExecutor;
        this.dispatchMode = (dispatchMode != null) ? dispatchMode : DispatchMode.ERRORS_AND_SLOW;
        this.slowThresholdMs = (slowThresholdMs > 0) ? slowThresholdMs : 3000;
        this.includeRequestIp = includeRequestIp;
        this.ignoreOptionsRequests = ignoreOptionsRequests;
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
        this.excludeMethods = excludeMethods == null ? List.of() : excludeMethods.stream()
                .filter(m -> m != null && !m.trim().isEmpty())
                .map(m -> m.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toList());
    }

    LogDispatchFilter(String serverUrl, String apiKey, List<String> maskedHeaders, List<String> excludePaths,
                      RestTemplate restTemplate, Executor dispatchExecutor, int timeoutMs) {
        this(true, serverUrl, apiKey, maskedHeaders, excludePaths, restTemplate, dispatchExecutor, timeoutMs,
                DEFAULT_MAX_STACK_FRAMES, DispatchMode.ERRORS_AND_SLOW, 3000, true);
    }

    LogDispatchFilter(boolean enabled, String serverUrl, String apiKey, List<String> maskedHeaders,
            List<String> excludePaths, RestTemplate restTemplate, Executor dispatchExecutor, int timeoutMs) {
        this(enabled, serverUrl, apiKey, maskedHeaders, excludePaths, restTemplate, dispatchExecutor, timeoutMs,
                DEFAULT_MAX_STACK_FRAMES, DispatchMode.ERRORS_AND_SLOW, 3000, true);
    }

    private boolean isMethodExcluded(String method) {
        if (method == null) {
            return false;
        }
        String upperMethod = method.trim().toUpperCase(Locale.ROOT);
        if (ignoreOptionsRequests && "OPTIONS".equals(upperMethod)) {
            return true;
        }
        if (excludeMethods.isEmpty()) {
            return false;
        }
        return excludeMethods.contains(upperMethod);
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

        if (isMethodExcluded(request.getMethod())) {
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

                        String path = requestToUse.getRequestURI();
                        String method = requestToUse.getMethod();
                        String customSeverity = (String) requestToUse.getAttribute("logdispatch.severity");

                        Throwable aspectEx = (Throwable) requestToUse.getAttribute("logdispatch.exception");
                        Throwable actualEx = unhandledException != null ? unhandledException : aspectEx;

                        Boolean isDeprecatedAttr = (Boolean) requestToUse.getAttribute("logdispatch.isDeprecated");
                        boolean isDeprecated = Boolean.TRUE.equals(isDeprecatedAttr);

                        List<String> mergedTags = collectTags(requestToUse, isError, isSlow, isDeprecated, status, responseSizeBytes);

                        boolean isController = isControllerRequest(requestToUse);

                        if (actualEx != null) {
                            String feature = (String) requestToUse.getAttribute("logdispatch.feature");
                            String api = (String) requestToUse.getAttribute("logdispatch.api");
                            String function = (String) requestToUse.getAttribute("logdispatch.function");

                            if (feature == null) {
                                feature = isController ? actualEx.getClass().getSimpleName() : "Filter/" + actualEx.getClass().getSimpleName();
                            }
                            if (api == null) api = path;
                            if (function == null) function = isController ? "UNKNOWN" : method + " " + path;

                            pushTelemetryAsync(path, method, customSeverity, traceId, spanId, parentSpanId, requestIp, isError, isDeprecated, status,
                                    actualEx.getClass().getSimpleName(), actualEx.getMessage(), formatStackTrace(actualEx),
                                    feature, api, function, executionTimeMs, responseSizeBytes, mergedTags, inputInfo, executionLogs, isController);
                        } else if (isError) {
                            Throwable filterEx = extractFilterException(requestToUse);
                            pushFilterErrorAsync(path, method, customSeverity, traceId, spanId, parentSpanId, requestIp, isDeprecated, status,
                                    executionTimeMs, responseSizeBytes, mergedTags, inputInfo, executionLogs, filterEx);
                        } else {
                            // Successful execution telemetry (2xx OK)
                            String feature = resolveFeature(requestToUse, "Controller");
                            String api = (String) requestToUse.getAttribute("logdispatch.api");
                            String function = resolveFunction(requestToUse, "handleRequest");

                            if (api == null) api = path;

                            pushSuccessAsync(path, method, traceId, spanId, parentSpanId, requestIp, isDeprecated, status,
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

    private String resolveFeature(HttpServletRequest request, String defaultFallback) {
        String feature = (String) request.getAttribute("logdispatch.feature");
        if (feature != null && !feature.isBlank()) {
            return feature;
        }
        Object handler = request.getAttribute("org.springframework.web.servlet.HandlerMapping.bestMatchingHandler");
        if (handler instanceof HandlerMethod handlerMethod) {
            return handlerMethod.getBeanType().getSimpleName();
        }
        return defaultFallback;
    }

    private String resolveFunction(HttpServletRequest request, String defaultFallback) {
        String function = (String) request.getAttribute("logdispatch.function");
        if (function != null && !function.isBlank()) {
            return function;
        }
        Object handler = request.getAttribute("org.springframework.web.servlet.HandlerMapping.bestMatchingHandler");
        if (handler instanceof HandlerMethod handlerMethod) {
            return handlerMethod.getMethod().getName();
        }
        return defaultFallback;
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

    private String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private String formatDuration(long ms) {
        if (ms <= 0) return "0.00s";
        return String.format(Locale.ROOT, "%.2fs", ms / 1000.0);
    }

    private void pushSuccessAsync(String path, String method, String traceId, String spanId, String parentSpanId,
                                  String requestIp, boolean isDeprecated, int statusCode, String feature, String api, String function,
                                  long executionTimeMs, long responseSizeBytes, List<String> tags,
                                  Map<String, Object> inputInfo, List<String> executionLogs) {
        dispatchAsync(() -> {
            try {
                String sizeFormatted = formatBytes(responseSizeBytes);
                String timeFormatted = formatDuration(executionTimeMs);
                String successMessage = ("Controller".equals(feature) || "handleRequest".equals(function))
                        ? "Successfully processed " + method + " " + path + " in " + timeFormatted + " (" + sizeFormatted + ")."
                        : "Successfully executed " + method + " " + feature + "." + function + " in " + timeFormatted + " (" + sizeFormatted + ").";

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
                        successMessage,
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

    private Throwable extractFilterException(HttpServletRequest request) {
        Object secEx = request.getAttribute("SPRING_SECURITY_LAST_EXCEPTION");
        if (secEx instanceof Throwable t) return t;

        Object jakartaEx = request.getAttribute("jakarta.servlet.error.exception");
        if (jakartaEx instanceof Throwable t) return t;

        Object javaxEx = request.getAttribute("javax.servlet.error.exception");
        if (javaxEx instanceof Throwable t) return t;

        return null;
    }

    private void pushFilterErrorAsync(String path, String method, String customSeverity, String traceId, String spanId, String parentSpanId,
                                       String requestIp, boolean isDeprecated, int statusCode, long executionTimeMs,
                                       long responseSizeBytes, List<String> tags, Map<String, Object> inputInfo,
                                       List<String> executionLogs, Throwable filterEx) {
        dispatchAsync(() -> {
            try {
                String severity = resolveSeverity(customSeverity, true, statusCode, false);

                String errorType;
                String errorMessage;
                String stackTrace;
                String feature;
                String function = method + " " + path;

                if (filterEx != null) {
                    errorType = filterEx.getClass().getSimpleName();
                    errorMessage = (filterEx.getMessage() != null && !filterEx.getMessage().isBlank())
                            ? filterEx.getMessage()
                            : "Filter error: " + errorType;
                    stackTrace = formatStackTrace(filterEx);
                    feature = "Filter/" + errorType;
                } else {
                    String reasonPhrase = resolveHttpStatusReason(statusCode);
                    errorType = resolveFilterErrorType(statusCode, reasonPhrase);
                    errorMessage = "Request failed with HTTP status " + statusCode + " (" + reasonPhrase + ") at filter level.";
                    stackTrace = "No Java exception captured in FilterChain (HTTP " + statusCode + ").";
                    feature = resolveFilterFeature(statusCode);
                }

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
                        severity,
                        tags,
                        statusCode,
                        errorType,
                        errorMessage,
                        path,
                        feature,
                        path,
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
                log.warn("[LogDispatch] Failed to push filter error: {}", e.getMessage());
            }
        });
    }

    private String resolveHttpStatusReason(int statusCode) {
        HttpStatus httpStatus = HttpStatus.resolve(statusCode);
        return httpStatus != null ? httpStatus.getReasonPhrase() : "HTTP " + statusCode;
    }

    private String resolveFilterErrorType(int statusCode, String reasonPhrase) {
        if (reasonPhrase != null && !reasonPhrase.isBlank()) {
            return reasonPhrase.replaceAll("[^a-zA-Z0-9]", "");
        }
        return "FilterError_" + statusCode;
    }

    private String resolveFilterFeature(int statusCode) {
        if (statusCode == 401 || statusCode == 403) {
            return "FilterSecurity";
        } else if (statusCode == 404) {
            return "FilterRouting";
        } else if (statusCode >= 400 && statusCode < 500) {
            return "FilterValidation";
        } else if (statusCode >= 500) {
            return "FilterInfrastructure";
        }
        return "FilterSecurity/Routing";
    }

    private boolean isControllerRequest(HttpServletRequest request) {
        Object handler = request.getAttribute("org.springframework.web.servlet.HandlerMapping.bestMatchingHandler");
        return handler instanceof HandlerMethod;
    }

    private String resolveSeverity(String customSeverity, boolean isError, int statusCode, boolean isControllerRequest) {
        if (customSeverity != null && !customSeverity.isBlank() && !LogSeverity.DEFAULT.name().equalsIgnoreCase(customSeverity)) {
            return customSeverity;
        }
        if (!isError) {
            return LogSeverity.SUCCESS.name();
        }
        if (statusCode == 401 || statusCode == 403) {
            return LogSeverity.SECURITY.name();
        }
        if (!isControllerRequest) {
            return LogSeverity.HTTP_FILTER_ERROR.name();
        }
        if (statusCode >= 500) {
            return LogSeverity.CRITICAL.name();
        }
        return LogSeverity.WARNING.name();
    }

    private void pushTelemetryAsync(String path, String method, String customSeverity, String traceId, String spanId, String parentSpanId,
                                    String requestIp, boolean isError, boolean isDeprecated, int statusCode, String errorType,
                                    String errorMessage, String stackTrace, String feature, String api, String function,
                                    long executionTimeMs, long responseSizeBytes, List<String> tags,
                                    Map<String, Object> inputInfo, List<String> executionLogs, boolean isControllerRequest) {
        dispatchAsync(() -> {
            try {
                String severity = resolveSeverity(customSeverity, isError, statusCode, isControllerRequest);

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
