package in.maheshlangote.logdispatch.annotation;

/**
 * Defines the severity levels for log events dispatched by LogDispatch.
 */
public enum LogSeverity {
    /**
     * Default severity. Uses automatic severity calculation based on HTTP status code:
     * 5xx -> CRITICAL, 4xx -> WARNING, Security Filter -> SECURITY.
     */
    DEFAULT,

    /**
     * Successful operational execution (2xx OK).
     */
    SUCCESS,

    /**
     * Diagnostic / Fine-grained informational events used for deep debugging.
     */
    DEBUG,

    /**
     * Informational events tracking key operational milestones.
     */
    INFO,

    /**
     * Non-fatal warnings, client-side validation errors, or recoverable issues.
     */
    WARNING,

    /**
     * Business logic failures or managed runtime exceptions that fail a request.
     */
    ERROR,

    /**
     * Severe system issues or core dependency outages requiring urgent engineer attention.
     */
    CRITICAL,

    /**
     * Security violations, unauthorized access (401/403), or authentication failures.
     */
    SECURITY,

    /**
     * Catastrophic application failure causing total service outage or crash.
     */
    FATAL
}
