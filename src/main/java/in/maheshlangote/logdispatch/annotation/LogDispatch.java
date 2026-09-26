package in.maheshlangote.logdispatch.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to customize or ignore metadata dispatched to the APM Server.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface LogDispatch {

    /**
     * Set to false to completely ignore and skip log dispatching for this controller or method.
     * e.g., @LogDispatch(enabled = false)
     * @return true if enabled, false to ignore completely
     */
    boolean enabled() default true;

    /**
     * Overrides the default affectedFeature (which is the Class name).
     * e.g., "PaymentProcessing"
     * @return the custom feature name
     */
    String feature() default "";

    /**
     * Overrides the default affectedAPI (which is the HTTP path).
     * e.g., "Stripe Webhook Listener"
     * @return the custom API name
     */
    String api() default "";

    /**
     * Overrides the default affectedFunction (which is the Method name).
     * e.g., "processPaymentAsync"
     * @return the custom function name
     */
    String function() default "";

    /**
     * Overrides the default severity.
     * e.g., LogSeverity.CRITICAL or LogSeverity.WARNING
     * @return the custom severity level
     */
    LogSeverity severity() default LogSeverity.DEFAULT;

    /**
     * Custom tags attached to the dispatched telemetry payload.
     * e.g., {"CRITICAL_PAYMENT", "VIP_FLOW"}
     * @return list of custom developer tags
     */
    String[] tags() default {};
}
