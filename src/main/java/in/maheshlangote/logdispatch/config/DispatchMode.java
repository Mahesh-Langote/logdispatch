package in.maheshlangote.logdispatch.config;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Dispatch modes controlling which HTTP request telemetries are pushed to the APM server.
 */
public enum DispatchMode {

    /**
     * Dispatches telemetry for all HTTP requests (100% full APM monitoring).
     */
    ALL("all"),

    /**
     * Dispatches telemetry only for requests that result in errors (HTTP status >= 400 or unhandled exception).
     */
    ERRORS_ONLY("errors-only"),

    /**
     * Dispatches telemetry for errors and requests exceeding slow-threshold-ms.
     */
    ERRORS_AND_SLOW("errors-and-slow");

    private final String value;

    DispatchMode(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static DispatchMode fromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return ALL;
        }
        String normalized = value.trim().toLowerCase();
        for (DispatchMode mode : values()) {
            if (mode.value.equalsIgnoreCase(normalized) || mode.name().equalsIgnoreCase(normalized.replace("-", "_"))) {
                return mode;
            }
        }
        return ALL;
    }

    @Override
    public String toString() {
        return value;
    }
}
