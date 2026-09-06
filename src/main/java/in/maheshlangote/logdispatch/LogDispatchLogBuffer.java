package in.maheshlangote.logdispatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Manages a thread-local log buffer for capturing logs generated during an HTTP request.
 */
public class LogDispatchLogBuffer {

    private static final ThreadLocal<List<ExecutionLogEntry>> THREAD_LOCAL_BUFFER = new ThreadLocal<>();

    private LogDispatchLogBuffer() {
        // Utility class
    }

    /**
     * Initializes a fresh log buffer for the current thread.
     */
    public static void init() {
        THREAD_LOCAL_BUFFER.set(new ArrayList<>());
    }

    /**
     * Appends an ExecutionLogEntry to the current thread's buffer if active and within limit/level.
     *
     * @param entry the log entry
     * @param maxEntries maximum allowed entries in buffer
     * @param minLevel minimum log level string (e.g. TRACE, DEBUG, INFO, WARN, ERROR)
     */
    public static void append(ExecutionLogEntry entry, int maxEntries, String minLevel) {
        List<ExecutionLogEntry> buffer = THREAD_LOCAL_BUFFER.get();
        if (buffer == null || entry == null) {
            return;
        }

        if (!isLevelAllowed(entry.level(), minLevel)) {
            return;
        }

        if (buffer.size() < maxEntries) {
            buffer.add(entry);
        }
    }

    /**
     * Retrieves captured logs for the current thread.
     *
     * @return unmodifiable list of captured logs, or empty list if none
     */
    public static List<ExecutionLogEntry> getLogs() {
        List<ExecutionLogEntry> buffer = THREAD_LOCAL_BUFFER.get();
        if (buffer == null || buffer.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(buffer));
    }

    /**
     * Clears the log buffer for the current thread to prevent memory leaks.
     */
    public static void clear() {
        THREAD_LOCAL_BUFFER.remove();
    }

    /**
     * Checks if the thread-local buffer is currently initialized for this thread.
     *
     * @return true if buffer is initialized
     */
    public static boolean isInitialized() {
        return THREAD_LOCAL_BUFFER.get() != null;
    }

    private static boolean isLevelAllowed(String eventLevel, String configuredMinLevel) {
        int eventScore = getLevelScore(eventLevel);
        int minScore = getLevelScore(configuredMinLevel);
        return eventScore >= minScore;
    }

    private static int getLevelScore(String level) {
        if (level == null) return 0;
        String trimmed = level.trim();
        if (trimmed.contains(",")) {
            String[] parts = trimmed.split(",");
            int minScore = Integer.MAX_VALUE;
            for (String part : parts) {
                int score = getSingleLevelScore(part.trim());
                if (score > 0 && score < minScore) {
                    minScore = score;
                }
            }
            return minScore == Integer.MAX_VALUE ? 0 : minScore;
        }
        return getSingleLevelScore(trimmed);
    }

    private static int getSingleLevelScore(String level) {
        if (level == null) return 0;
        switch (level.toUpperCase(Locale.ROOT)) {
            case "TRACE":
                return 1;
            case "DEBUG":
                return 2;
            case "INFO":
                return 3;
            case "WARN":
            case "WARNING":
                return 4;
            case "ERROR":
                return 5;
            default:
                return 0;
        }
    }
}
