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

    public LogDispatchFilter(String serverUrl, String apiKey) {
        this.serverUrl = serverUrl;
        this.apiKey = apiKey;
        this.restTemplate = new RestTemplate();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        try {
            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            // Spring filters might throw exceptions up the chain, catch and let it bubble up, but log it first if needed.
            // Note: Standard Spring MVC usually handles exceptions via ControllerAdvice, which sets the response status but consumes the exception.
            throw ex;
        } finally {
            // After the request has been processed, inspect the status.
            int status = response.getStatus();
            
            if (status >= 400) {
                // Check if the Aspect already handled an exception for this request
                Object handled = request.getAttribute("logdispatch.handled");
                if (handled == null || !(Boolean) handled) {
                    pushErrorAsync(request, status);
                }
            }
        }
    }

    private void pushErrorAsync(HttpServletRequest request, int statusCode) {
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
}
