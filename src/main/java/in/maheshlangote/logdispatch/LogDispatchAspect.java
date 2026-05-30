package in.maheshlangote.logdispatch;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;

import org.aspectj.lang.reflect.MethodSignature;
import in.maheshlangote.logdispatch.annotation.LogDispatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * An aspect that automatically intercepts and dispatches all unhandled exceptions
 * thrown by Spring Boot RestControllers.
 * 
 * This aspect runs before the exception reaches any global {@link org.springframework.web.bind.annotation.ControllerAdvice},
 * ensuring that the exact exception and its stack trace are pushed to the centralized
 * LogDispatch server asynchronously.
 *
 * @author Mahesh Langote
 * @version 1.0.0
 */
@Aspect
public class LogDispatchAspect {

    private static final Logger log = LoggerFactory.getLogger(LogDispatchAspect.class);

    private final String serverUrl;
    private final String apiKey;
    private final RestTemplate restTemplate;

    /**
     * Constructs a new LogDispatchAspect.
     *
     * @param serverUrl the endpoint URL of the centralized APM server.
     * @param apiKey the authentication key required by the APM server.
     */
    public LogDispatchAspect(String serverUrl, String apiKey) {
        this.serverUrl = serverUrl;
        this.apiKey = apiKey;
        this.restTemplate = new RestTemplate();
    }

    /**
     * Pointcut advisor that hooks into any method execution within a class annotated
     * with {@link org.springframework.web.bind.annotation.RestController}.
     *
     * @param joinPoint the AOP join point containing method metadata.
     * @param ex the exception that was thrown by the controller.
     */
    @AfterThrowing(pointcut = "within(@org.springframework.web.bind.annotation.RestController *)", throwing = "ex")
    public void handleControllerException(JoinPoint joinPoint, Throwable ex) {
        String path = "UNKNOWN";
        String httpMethod = "UNKNOWN";
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                path = request.getRequestURI();
                httpMethod = request.getMethod();
                request.setAttribute("logdispatch.handled", true);
            }
        } catch (Exception ignored) {}

        // Defaults
        String feature = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String function = joinPoint.getSignature().getName();
        String api = path;

        // Try to read the custom annotation from the Method or Class
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            LogDispatch annotation = method.getAnnotation(LogDispatch.class);
            if (annotation == null) {
                annotation = joinPoint.getTarget().getClass().getAnnotation(LogDispatch.class);
            }

            if (annotation != null) {
                if (!annotation.feature().isEmpty()) feature = annotation.feature();
                if (!annotation.api().isEmpty()) api = annotation.api();
                if (!annotation.function().isEmpty()) function = annotation.function();
            }
        } catch (Exception ignored) {}

        pushErrorAsync(ex, path, feature, api, function, httpMethod);
    }

    /**
     * Asynchronously pushes the exception details to the LogDispatch server.
     * Fails silently if the server is unreachable to prevent impacting the client application.
     */
    private void pushErrorAsync(Throwable ex, String errorPath, String feature, String api, String function, String httpMethod) {
        CompletableFuture.runAsync(() -> {
            try {
                int statusCode = 500; // Default for unhandled exceptions
                if (ex instanceof org.springframework.web.server.ResponseStatusException) {
                    statusCode = ((org.springframework.web.server.ResponseStatusException) ex).getStatusCode().value();
                } else {
                    org.springframework.web.bind.annotation.ResponseStatus responseStatus = 
                        org.springframework.core.annotation.AnnotationUtils.findAnnotation(ex.getClass(), org.springframework.web.bind.annotation.ResponseStatus.class);
                    if (responseStatus != null) {
                        statusCode = responseStatus.value().value();
                    }
                }

                String severity = (statusCode >= 500) ? "CRITICAL" : "WARNING";

                Map<String, Object> payload = new HashMap<>();
                payload.put("timestamp", Instant.now().toString());
                payload.put("errorType", ex.getClass().getSimpleName());
                payload.put("statusCode", statusCode);
                payload.put("errorMessage", ex.getMessage());
                payload.put("errorPath", errorPath);
                payload.put("affectedFeature", feature);
                payload.put("affectedAPI", api);
                payload.put("apiType", httpMethod);
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

                HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
                
                restTemplate.postForEntity(serverUrl, request, String.class);
            } catch (HttpClientErrorException | HttpServerErrorException e) {
                log.warn("[LogDispatch] Failed to push error: {} : {}", e.getStatusCode(), e.getResponseBodyAsString());
            } catch (Exception e) {
                log.warn("[LogDispatch] Failed to push error: {}", e.getMessage());
            }
        });
    }
}
