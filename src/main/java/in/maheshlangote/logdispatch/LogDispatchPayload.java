package in.maheshlangote.logdispatch;

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
        Map<String, Object> inputInformation
) {}
