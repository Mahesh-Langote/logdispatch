package in.maheshlangote.logdispatch;

import java.util.List;
import java.util.Map;

/**
 * Represents the JSON payload dispatched to the centralized APM server.
 */
public record LogDispatchPayload(
        String timestamp,
        String traceId,
        String spanId,
        String parentSpanId,
        String sdkVersion,
        String sdkLanguage,
        String requestIp,
        boolean isError,
        boolean isDeprecated,
        String severity,
        List<String> tags,
        int statusCode,
        String errorType,
        String errorMessage,
        String errorPath,
        String affectedFeature,
        String affectedAPI,
        String apiType,
        String affectedFunction,
        String stackTrace,
        long executionTimeMs,
        long responseSizeBytes,
        Map<String, Object> performanceBreakdown,
        Map<String, Object> systemHealth,
        Map<String, Object> inputInformation,
        List<String> executionLogs
) {
    /**
     * Backward-compatible constructor for 13-argument calls.
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
            Map<String, Object> inputInformation,
            List<String> executionLogs
    ) {
        this(
                timestamp,
                null,
                null,
                null,
                LogDispatchVersion.getSdkVersion(),
                LogDispatchVersion.SDK_LANGUAGE,
                null,
                statusCode >= 400,
                false,
                severity,
                List.of(),
                statusCode,
                errorType,
                errorMessage,
                errorPath,
                affectedFeature,
                affectedAPI,
                apiType,
                affectedFunction,
                stackTrace,
                0L,
                0L,
                null,
                null,
                inputInformation,
                executionLogs
        );
    }

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
        this(
                timestamp,
                errorType,
                statusCode,
                errorMessage,
                errorPath,
                affectedFeature,
                affectedAPI,
                apiType,
                affectedFunction,
                stackTrace,
                severity,
                inputInformation,
                List.of()
        );
    }
}
