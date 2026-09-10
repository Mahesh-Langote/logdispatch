package in.maheshlangote.logdispatch.annotation;

/**
 * Defines the severity levels for log events dispatched by LogDispatch.
 * 
 * Standard practice guidance for choosing severity levels:
 * <ul>
 *   <li><b>DEFAULT</b>: Automatically evaluates HTTP status (5xx -> CRITICAL, 4xx -> WARNING, Auth -> SECURITY).</li>
 *   <li><b>DEBUG</b>: Fine-grained diagnostic information during development or troubleshooting.</li>
 *   <li><b>INFO</b>: Expected operational events, lifecycle state changes, or routine notifications.</li>
 *   <li><b>WARNING</b>: Unexpected situations or non-fatal client errors (e.g. invalid input, retryable operation).</li>
 *   <li><b>ERROR</b>: Application exceptions or business logic failures that prevent request completion.</li>
 *   <li><b>CRITICAL</b>: Major service disruptions, third-party dependency outages, or system failures needing immediate action.</li>
 *   <li><b>SECURITY</b>: Authentication breaches, invalid tokens, permission violations, or rate-limiting events.</li>
 *   <li><b>FATAL</b>: Total service breakdown, database connection pool exhaustion, or unrecoverable core component failure.</li>
 * </ul>
 */
public enum LogSeverity {
    /**
     * Default severity. Uses automatic severity calculation based on HTTP status code:
     * 5xx -> CRITICAL, 4xx -> WARNING, Security Filter -> SECURITY.
     */
    DEFAULT,

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
