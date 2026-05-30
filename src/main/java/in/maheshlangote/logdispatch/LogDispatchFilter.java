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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Filter that captures all unhandled HTTP errors (e.g. 401, 403, 404, 500)
 * that occur outside of a Spring RestController (such as in security filters).
 */
public class LogDispatchFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LogDispatchFilter.class);

    private final String serverUrl;
    private final String apiKey;
    private final RestTemplate restTemplate;

    /**
     * Constructs the LogDispatchFilter.
     *
     * @param serverUrl the endpoint URL of the centralized APM server
     * @param apiKey the authentication key required by the APM server
     */
    public LogDispatchFilter(String serverUrl, String apiKey) {
        this.serverUrl = serverUrl;
        this.apiKey = apiKey;
        this.restTemplate = new RestTemplate();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        Throwable unhandledException = null;
        try {
            filterChain.doFilter(request, response);
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
                Throwable aspectEx = (Throwable) request.getAttribute("logdispatch.exception");
                Throwable actualEx = unhandledException != null ? unhandledException : aspectEx;

                if (actualEx != null) {
                    // We have exception details (either from the Aspect or from an unhandled filter exception)
                    String feature = (String) request.getAttribute("logdispatch.feature");
                    String api = (String) request.getAttribute("logdispatch.api");
                    String function = (String) request.getAttribute("logdispatch.function");
                    
                    if (feature == null) feature = actualEx.getClass().getSimpleName();
                    if (api == null) api = request.getRequestURI();
                    if (function == null) function = "UNKNOWN";

                    pushErrorAsync(request, status, actualEx, feature, api, function);
                } else {
                    // Pure filter-level error (e.g. 403 Forbidden via security filter, no exception thrown)
                    pushFilterErrorAsync(request, status);
                }
            }
        }
    }

    private void pushFilterErrorAsync(HttpServletRequest request, int statusCode) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        
        CompletableFuture.runAsync(() -> {
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

    private void pushErrorAsync(HttpServletRequest request, int statusCode, Throwable ex, String feature, String api, String function) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        
        CompletableFuture.runAsync(() -> {
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
}
