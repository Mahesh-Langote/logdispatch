package in.maheshlangote.logdispatch;

import in.maheshlangote.logdispatch.config.LogLevel;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages thread-inheritable and traceId-correlated log buffers for capturing execution logs.
 * Captures 100% of console logs generated during execution up to 128 KB.
 */
public class LogDispatchLogBuffer {

    /** Maximum allowed buffer size in bytes/characters (128 KB). */
    public static final int MAX_BUFFER_BYTES = 128 * 1024;

    private static final String TRUNCATION_NOTICE =
            "[LogDispatch] Execution log limit reached (128 KB). Subsequent logs truncated.";

    // InheritableThreadLocal allows child threads spawned during a request to inherit the parent's log buffer
    private static final ThreadLocal<BufferState> THREAD_LOCAL_BUFFER = new InheritableThreadLocal<>();

    // Map keyed by traceId to support async worker threads referencing the request's traceId
    private static final Map<String, BufferState> TRACE_LOG_BUFFERS = new ConcurrentHashMap<>();

    private static class BufferState {
        private final List<String> logs = new ArrayList<>();
        private int currentBytes = 0;
        private boolean limitReached = false;
        private final long createdAt = System.currentTimeMillis();
    }

    private LogDispatchLogBuffer() {
        // Utility class
    }

    /**
     * Initializes a fresh log buffer for the current thread.
     */
    public static void init() {
        init(null);
    }

    /**
     * Initializes a fresh log buffer for the current thread and binds it to a traceId.
     *
     * @param traceId the transaction correlation ID
     */
    public static void init(String traceId) {
        BufferState state = new BufferState();
        THREAD_LOCAL_BUFFER.set(state);
        if (traceId != null && !traceId.isBlank()) {
            TRACE_LOG_BUFFERS.put(traceId, state);
        }
    }

    /**
     * Appends a formatted log string to the current execution buffer.
     * Captures 100% of logs without dropping based on log levels.
     *
     * @param formattedLog the formatted log line
     */
    public static void append(String formattedLog) {
        if (formattedLog == null) {
            return;
        }

        BufferState state = getActiveBufferState();
        if (state == null) {
            return;
        }

        synchronized (state) {
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
    }

    /**
     * Backward-compatible append method with level arguments (level filtering bypassed for 100% capture).
     */
    public static void append(String formattedLog, LogLevel minLevel, LogLevel eventLevel) {
        append(formattedLog);
    }

    public static void append(String formattedLog, String minLevel, String eventLevel) {
        append(formattedLog);
    }

    /**
     * Backward-compatible append method for ExecutionLogEntry.
     */
    public static void append(ExecutionLogEntry entry, int maxEntries, LogLevel minLevel) {
        append(entry, maxEntries, minLevel != null ? minLevel.name() : "DEBUG");
    }

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
        append(sb.toString());
    }

    /**
     * Retrieves captured logs for the current thread or active traceId.
     *
     * @return unmodifiable list of captured logs
     */
    public static List<String> getLogs() {
        BufferState state = getActiveBufferState();
        if (state == null) {
            return Collections.emptyList();
        }

        synchronized (state) {
            if (state.logs.isEmpty()) {
                return Collections.emptyList();
            }
            return Collections.unmodifiableList(new ArrayList<>(state.logs));
        }
    }

    /**
     * Clears the log buffer for the current thread and traceId context.
     */
    public static void clear() {
        String traceId = MDC.get("traceId");
        if (traceId != null && !traceId.isBlank()) {
            TRACE_LOG_BUFFERS.remove(traceId);
        }
        THREAD_LOCAL_BUFFER.remove();
    }

    /**
     * Checks if a log buffer is initialized for the current thread context.
     *
     * @return true if initialized
     */
    public static boolean isInitialized() {
        return getActiveBufferState() != null;
    }

    private static BufferState getActiveBufferState() {
        BufferState state = THREAD_LOCAL_BUFFER.get();
        if (state != null) {
            return state;
        }

        String traceId = MDC.get("traceId");
        if (traceId != null && !traceId.isBlank()) {
            return TRACE_LOG_BUFFERS.get(traceId);
        }

        return null;
    }
}
