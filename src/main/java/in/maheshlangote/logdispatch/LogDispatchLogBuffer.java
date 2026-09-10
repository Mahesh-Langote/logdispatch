package in.maheshlangote.logdispatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Manages a thread-local log buffer for capturing logs generated during an HTTP request.
 * Automatically caps total accumulated execution log size to 128 KB to prevent payload bloat
 * and database truncation.
 */
public class LogDispatchLogBuffer {

    /** Maximum allowed buffer size in bytes/characters (128 KB). */
    public static final int MAX_BUFFER_BYTES = 128 * 1024;

    private static final String TRUNCATION_NOTICE =
            "[LogDispatch] Execution log limit reached (128 KB). Subsequent logs truncated.";

    private static final ThreadLocal<BufferState> THREAD_LOCAL_BUFFER = new ThreadLocal<>();

    private static class BufferState {
        private final List<String> logs = new ArrayList<>();
        private int currentBytes = 0;
        private boolean limitReached = false;
    }

    private LogDispatchLogBuffer() {
        // Utility class
    }

    /**
     * Initializes a fresh log buffer for the current thread.
     */
    public static void init() {
        THREAD_LOCAL_BUFFER.set(new BufferState());
    }

    /**
     * Appends a formatted log string to the current thread's buffer if active and within 128 KB limit.
     *
     * @param formattedLog the console-style formatted log line
     * @param minLevel minimum log level string (e.g. TRACE, DEBUG, INFO, WARN, ERROR)
     * @param eventLevel level of the current event
     */
    public static void append(String formattedLog, String minLevel, String eventLevel) {
        BufferState state = THREAD_LOCAL_BUFFER.get();
        if (state == null || formattedLog == null) {
            return;
        }

        if (!isLevelAllowed(eventLevel, minLevel)) {
            return;
        }

        if (state.limitReached) {
            return;
        }

        int logLength = formattedLog.length();
        if (state.currentBytes + logLength <= MAX_BUFFER_BYTES) {
            state.logs.add(formattedLog);
            state.currentBytes += logLength;
        } else {
            state.limitReached = true;
            state.logs.add(TRUNCATION_NOTICE);
        }
    }

    /**
     * Backward-compatible append method for ExecutionLogEntry.
     */
    public static void append(ExecutionLogEntry entry, int maxEntries, String minLevel) {
        if (entry == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append(entry.timestamp()).append(" ")
          .append(entry.level()).append(" ")
          .append(entry.loggerName()).append(" : ")
          .append(entry.message());
        if (entry.throwable() != null) {
            sb.append("\n").append(entry.throwable());
        }
        append(sb.toString(), minLevel, entry.level());
    }

    /**
     * Retrieves captured logs for the current thread.
     *
     * @return unmodifiable list of captured logs, or empty list if none
     */
    public static List<String> getLogs() {
        BufferState state = THREAD_LOCAL_BUFFER.get();
        if (state == null || state.logs.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(state.logs));
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
