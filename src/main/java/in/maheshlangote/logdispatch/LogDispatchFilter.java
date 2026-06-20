package in.maheshlangote.logdispatch;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.Enumeration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Filter that captures all unhandled HTTP errors (e.g. 401, 403, 404, 500)
 * that occur outside of a Spring RestController (such as in security filters).
 */
public class LogDispatchFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LogDispatchFilter.class);

    private final String serverUrl;
    private final String apiKey;
    private final RestTemplate restTemplate;
    private final int timeoutMs;
    private final Set<String> maskedHeaders;
    private final List<String> excludePaths;
    private final Executor dispatchExecutor;
    
    private static final AntPathMatcher ANT_PATH_MATCHER = new AntPathMatcher();

    /**
     * Constructs the LogDispatchFilter.
     *
     * @param serverUrl the endpoint URL of the centralized APM server
     * @param apiKey the authentication key required by the APM server
     * @param maskedHeaders list of headers to mask
     * @param excludePaths list of URI paths to exclude from logging (supports wildcard patterns)
     * @param timeoutMs the HTTP connection and read timeout in milliseconds
     */
    public LogDispatchFilter(String serverUrl, String apiKey, List<String> maskedHeaders, List<String> excludePaths, int timeoutMs) { 
        this(serverUrl, apiKey, maskedHeaders, excludePaths, new RestTemplate(), null, timeoutMs);
    }

    LogDispatchFilter(String serverUrl, String apiKey, List<String> maskedHeaders, List<String> excludePaths,
                      RestTemplate restTemplate, Executor dispatchExecutor, int timeoutMs) {
        this.serverUrl = serverUrl;
        this.apiKey = apiKey;
        this.timeoutMs = timeoutMs;
        this.dispatchExecutor = dispatchExecutor;
        this.restTemplate = Objects.requireNonNull(restTemplate, "restTemplate");
        
        // Configure the timeout on the RestTemplate's underlying request factory
        if (this.restTemplate.getRequestFactory() instanceof SimpleClientHttpRequestFactory) {
            SimpleClientHttpRequestFactory factory = (SimpleClientHttpRequestFactory) this.restTemplate.getRequestFactory();
            factory.setConnectTimeout(timeoutMs);
            factory.setReadTimeout(timeoutMs);
        } else {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(timeoutMs);
            factory.setReadTimeout(timeoutMs);
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

    private static final int MAX_PAYLOAD_SIZE = 32 * 1024; // 32 KB

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
            return false; // Skip file uploads
        }
        int contentLength = request.getContentLength();
        if (contentLength > MAX_PAYLOAD_SIZE) {
            return false; // Skip large payloads
        }
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        // Check if the request path is excluded
        String requestPath = request.getRequestURI();
        if (isPathExcluded(requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }
        
        // Wrap the request to cache the input stream so we can log the body later if needed
        // but avoid wrapping if it's a file upload or a huge payload to prevent OutOfMemory issues.
        HttpServletRequest requestToUse = request;
        if (shouldWrapRequest(request)) {
            requestToUse = new ContentCachingRequestWrapper(request);
        }
        
        Throwable unhandledException = null;
        try {
            filterChain.doFilter(requestToUse, response);
        } catch (Exception ex) {
            unhandledException = ex;
            throw ex;
        } finally {
            // After the request has been processed, inspect the final status.
            int status = response.getStatus();
            if (unhandledException != null) {
                // Unhandled exceptions bubbling out of the filter chain result in a 500
                status = 500;
            }
            
            if (status >= 400) {
                Map<String, Object> inputInfo = extractInputInformation(requestToUse);

                Throwable aspectEx = (Throwable) requestToUse.getAttribute("logdispatch.exception");
                Throwable actualEx = unhandledException != null ? unhandledException : aspectEx;

                if (actualEx != null) {
                    // We have exception details (either from the Aspect or from an unhandled filter exception)
                    String feature = (String) requestToUse.getAttribute("logdispatch.feature");
                    String api = (String) requestToUse.getAttribute("logdispatch.api");
                    String function = (String) requestToUse.getAttribute("logdispatch.function");
                    
                    if (feature == null) feature = actualEx.getClass().getSimpleName();
                    if (api == null) api = requestToUse.getRequestURI();
                    if (function == null) function = "UNKNOWN";

                    pushErrorAsync(requestToUse, status, actualEx, feature, api, function, inputInfo);
                } else {
                    // Pure filter-level error (e.g. 403 Forbidden via security filter, no exception thrown)
                    pushFilterErrorAsync(requestToUse, status, inputInfo);
                }
            }
        }
    }

    private void pushFilterErrorAsync(HttpServletRequest request, int statusCode, Map<String, Object> inputInfo) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        
        dispatchAsync(() -> {
            try {
                String severity = (statusCode >= 500) ? "CRITICAL" : "WARNING";

                Map<String, Object> payload = new HashMap<>();
                payload.put("timestamp", Instant.now().toString());
                payload.put("errorType", "FilterError");
                payload.put("statusCode", statusCode);
                payload.put("errorMessage", "Request failed with status " + statusCode + " at filter level.");
                payload.put("errorPath", path);
                payload.put("affectedFeature", "FilterSecurity/Routing");
                payload.put("affectedAPI", path);
                payload.put("apiType", method);
                payload.put("affectedFunction", "doFilter");
                payload.put("stackTrace", "No stack trace available for filter-level status codes.");
                payload.put("severity", severity);
                payload.put("inputInformation", inputInfo);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("X-API-KEY", apiKey);

                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
                
                restTemplate.postForEntity(serverUrl, entity, String.class);
            } catch (HttpClientErrorException | HttpServerErrorException e) {
                log.warn("[LogDispatch] Failed to push filter error: {} : {}", e.getStatusCode(), e.getResponseBodyAsString());
            } catch (Exception e) {
                log.warn("[LogDispatch] Failed to push filter error: {}", e.getMessage());
            }
        });
    }

    private void pushErrorAsync(HttpServletRequest request, int statusCode, Throwable ex, String feature, String api, String function, Map<String, Object> inputInfo) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        
        dispatchAsync(() -> {
            try {
                String severity = (statusCode >= 500) ? "CRITICAL" : "WARNING";

                Map<String, Object> payload = new HashMap<>();
                payload.put("timestamp", Instant.now().toString());
                payload.put("errorType", ex.getClass().getSimpleName());
                payload.put("statusCode", statusCode);
                payload.put("errorMessage", ex.getMessage());
                payload.put("errorPath", path);
                payload.put("affectedFeature", feature);
                payload.put("affectedAPI", api);
                payload.put("apiType", method);
                payload.put("affectedFunction", function);
                
                StringBuilder stackTrace = new StringBuilder();
                for (StackTraceElement element : ex.getStackTrace()) {
                    stackTrace.append(element.toString()).append("\n");
                }
                payload.put("stackTrace", stackTrace.toString());
                payload.put("severity", severity);
                payload.put("inputInformation", inputInfo);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("X-API-KEY", apiKey);

                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
                
                restTemplate.postForEntity(serverUrl, entity, String.class);
            } catch (HttpClientErrorException | HttpServerErrorException e) {
                log.warn("[LogDispatch] Failed to push error: {} : {}", e.getStatusCode(), e.getResponseBodyAsString());
            } catch (Exception e) {
                log.warn("[LogDispatch] Failed to push error: {}", e.getMessage());
            }
        });
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
        
        if (request instanceof ContentCachingRequestWrapper) {
            ContentCachingRequestWrapper wrapper = (ContentCachingRequestWrapper) request;
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