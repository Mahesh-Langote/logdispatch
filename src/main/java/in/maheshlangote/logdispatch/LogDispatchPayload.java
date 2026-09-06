package in.maheshlangote.logdispatch;

import java.util.List;
import java.util.Map;

/**
 * Represents the JSON payload dispatched to the centralized APM server.
 */
public record LogDispatchPayload(
        String timestamp,
        String errorType,
        int statusCode,
        String errorMessage,
        String errorPath,
        String affectedFeature,
        String affectedAPI,
        String apiType,
        String affectedFunction,
        String stackTrace,
        String severity,
        Map<String, Object> inputInformation,
        List<ExecutionLogEntry> executionLogs
) {
    /**
     * Backward-compatible constructor for 12-argument calls.
     */
    public LogDispatchPayload(
            String timestamp,
            String errorType,
            int statusCode,
            String errorMessage,
            String errorPath,
            String affectedFeature,
            String affectedAPI,
            String apiType,
            String affectedFunction,
            String stackTrace,
            String severity,
            Map<String, Object> inputInformation
    ) {
        this(timestamp, errorType, statusCode, errorMessage, errorPath, affectedFeature, affectedAPI, apiType, affectedFunction, stackTrace, severity, inputInformation, List.of());
    }
}
