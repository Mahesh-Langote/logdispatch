package in.maheshlangote.logdispatch;

import in.maheshlangote.logdispatch.annotation.LogDispatch;
import in.maheshlangote.logdispatch.annotation.LogSeverity;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * An aspect that intercepts unhandled exceptions in Spring Boot RestControllers
 * and non-HTTP background components.
 */
@Aspect
public class LogDispatchAspect {

    private final boolean enabled;

    public LogDispatchAspect() {
        this(true);
    }

    public LogDispatchAspect(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Intercepts exceptions in RestControllers and custom @LogDispatch methods.
     *
     * @param joinPoint metadata about the execution point
     * @param ex the exception thrown
     */
    @AfterThrowing(
            pointcut = "within(@org.springframework.web.bind.annotation.RestController *) || @annotation(in.maheshlangote.logdispatch.annotation.LogDispatch)",
            throwing = "ex"
    )
    public void handleControllerException(JoinPoint joinPoint, Throwable ex) {
        if (!enabled) {
            return;
        }

        String path = "UNKNOWN";
        ServletRequestAttributes attributes = null;
        try {
            attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                path = request.getRequestURI();
                request.setAttribute("logdispatch.handled", true);
            }
        } catch (Exception ignored) {}

        String feature = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String function = joinPoint.getSignature().getName();
        String api = path;
        String severity = null;
        List<String> annotationTags = new ArrayList<>();
        boolean isDeprecated = false;

        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            Class<?> declaringClass = joinPoint.getTarget().getClass();

            // Check if @LogDispatch(enabled = false) is specified
            LogDispatch annotation = method.getAnnotation(LogDispatch.class);
            if (annotation == null) {
                annotation = declaringClass.getAnnotation(LogDispatch.class);
            }

            if (annotation != null && !annotation.enabled()) {
                if (attributes != null) {
                    attributes.getRequest().setAttribute("logdispatch.ignored", true);
                }
                return;
            }

            // Detect @Deprecated
            if (method.isAnnotationPresent(Deprecated.class) || declaringClass.isAnnotationPresent(Deprecated.class)) {
                isDeprecated = true;
            }

            if (annotation != null) {
                if (!annotation.feature().isEmpty()) feature = annotation.feature();
                if (!annotation.api().isEmpty()) api = annotation.api();
                if (!annotation.function().isEmpty()) function = annotation.function();
                if (annotation.severity() != LogSeverity.DEFAULT) severity = annotation.severity().name();
                if (annotation.tags().length > 0) {
                    annotationTags.addAll(Arrays.asList(annotation.tags()));
                }
            }
        } catch (Exception ignored) {}

        if (attributes != null) {
            try {
                HttpServletRequest request = attributes.getRequest();
                request.setAttribute("logdispatch.exception", ex);
                request.setAttribute("logdispatch.feature", feature);
                request.setAttribute("logdispatch.api", api);
                request.setAttribute("logdispatch.function", function);
                request.setAttribute("logdispatch.isDeprecated", isDeprecated);
                if (severity != null) {
                    request.setAttribute("logdispatch.severity", severity);
                }
                if (!annotationTags.isEmpty()) {
                    request.setAttribute("logdispatch.tags", annotationTags);
                }
            } catch (Exception ignored) {}
        }
    }
}
