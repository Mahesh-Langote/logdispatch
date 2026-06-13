package in.maheshlangote.logdispatch;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A public controller that exposes the health and uptime of the application
 * to the LogDispatch APM Server.
 */
@RestController
@RequestMapping("/logdispatch/health")
public class LogDispatchHealthController {

    private final Instant startupTime;
    
    // Rate Limiting (configurable requests per minute per IP)
    private final ConcurrentHashMap<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();
    private long currentWindowStart = System.currentTimeMillis();
    @Value("${logdispatch.health.rate-limit-per-minute:60}")
    private int maxRequestsPerMinute;
    
    private static final long WINDOW_SIZE_MS = 60000;

    /**
     * Initializes the health controller and records the startup time.
     */
    public LogDispatchHealthController() {
        this.startupTime = Instant.now();
    }

    /**
 * Returns the health status and uptime of the application.
 * Enforces a configurable rate limit per IP.
 *
 * @param request the HTTP request, used to determine the client IP
 * @return a ResponseEntity containing the status, startup time, and uptime
 */
    @GetMapping
    public ResponseEntity<Map<String, Object>> healthCheck(HttpServletRequest request) {
        String clientIp = getClientIp(request);
        
        if (!isAllowed(clientIp)) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "RATE_LIMITED");
            errorResponse.put("message", "Too many requests. Limit is " + maxRequestsPerMinute + " requests per minute.");
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(errorResponse);
        }

        long uptimeSeconds = Instant.now().getEpochSecond() - startupTime.getEpochSecond();

        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("startupTime", startupTime.toString());
        response.put("uptimeSeconds", uptimeSeconds);

        return ResponseEntity.ok(response);
    }
    
    private boolean isAllowed(String ip) {
        long now = System.currentTimeMillis();
        // Reset the window if 60 seconds have passed
        if (now - currentWindowStart > WINDOW_SIZE_MS) {
            synchronized (this) {
                if (now - currentWindowStart > WINDOW_SIZE_MS) {
                    requestCounts.clear();
                    currentWindowStart = now;
                }
            }
        }
        
        int count = requestCounts.computeIfAbsent(ip, k -> new AtomicInteger(0)).incrementAndGet();
        return count <= maxRequestsPerMinute;
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
