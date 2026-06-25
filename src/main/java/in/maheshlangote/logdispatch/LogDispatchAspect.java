package in.maheshlangote.logdispatch;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;

import org.aspectj.lang.reflect.MethodSignature;
import in.maheshlangote.logdispatch.annotation.LogDispatch;
import java.lang.reflect.Method;

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

    private final boolean enabled;

    /**
     * Constructs a new LogDispatchAspect.
     */
    public LogDispatchAspect() {
        this(true);
    }

    /**
     * Constructs a new LogDispatchAspect.
     *
     * @param enabled whether LogDispatch should capture controller exceptions
     */
    public LogDispatchAspect(boolean enabled) {
        this.enabled = enabled;
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
        if (!enabled) {
            return;
        }

        String path = "UNKNOWN";
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                path = request.getRequestURI();
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

        // Store details in request so the Filter can capture the final status code
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                request.setAttribute("logdispatch.exception", ex);
                request.setAttribute("logdispatch.feature", feature);
                request.setAttribute("logdispatch.api", api);
                request.setAttribute("logdispatch.function", function);
            }
        } catch (Exception ignored) {}
    }
}
