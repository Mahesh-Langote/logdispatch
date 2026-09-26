package in.maheshlangote.logdispatch.config;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Log levels for execution log capture filtering.
 */
public enum LogLevel {

    TRACE(1),
    DEBUG(2),
    INFO(3),
    WARN(4),
    ERROR(5);

    private final int severity;

    LogLevel(int severity) {
        this.severity = severity;
    }

    public int getSeverity() {
        return severity;
    }

    @JsonValue
    public String getValue() {
        return name();
    }

    public boolean isGreaterOrEqual(LogLevel other) {
        if (other == null) return true;
        return this.severity >= other.severity;
    }

    public static LogLevel fromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return DEBUG;
        }
        try {
            return LogLevel.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return DEBUG;
        }
    }
}
