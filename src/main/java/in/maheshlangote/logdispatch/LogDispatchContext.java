package in.maheshlangote.logdispatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Thread-local context for managing dynamic runtime tags and correlation metadata.
 */
public final class LogDispatchContext {

    private static final ThreadLocal<Set<String>> THREAD_LOCAL_TAGS = ThreadLocal.withInitial(HashSet::new);
    private static final ThreadLocal<String> THREAD_LOCAL_TRACE_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> THREAD_LOCAL_SPAN_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> THREAD_LOCAL_PARENT_SPAN_ID = new ThreadLocal<>();

    private LogDispatchContext() {
        // Utility class
    }

    /**
     * Adds a dynamic custom tag to the current execution context.
     *
     * @param tag the tag string to add
     */
    public static void addTag(String tag) {
        if (tag != null && !tag.isBlank()) {
            THREAD_LOCAL_TAGS.get().add(tag.trim());
        }
    }

    /**
     * Adds multiple dynamic custom tags to the current execution context.
     *
     * @param tags list of tags to add
     */
    public static void addTags(List<String> tags) {
        if (tags != null) {
            for (String tag : tags) {
                addTag(tag);
            }
        }
    }

    /**
     * Retrieves all dynamic custom tags associated with the current thread context.
     *
     * @return unmodifiable set of tags
     */
    public static List<String> getTags() {
        Set<String> tags = THREAD_LOCAL_TAGS.get();
        if (tags == null || tags.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(tags);
    }

    public static void setTraceId(String traceId) {
        THREAD_LOCAL_TRACE_ID.set(traceId);
    }

    public static String getTraceId() {
        return THREAD_LOCAL_TRACE_ID.get();
    }

    public static void setSpanId(String spanId) {
        THREAD_LOCAL_SPAN_ID.set(spanId);
    }

    public static String getSpanId() {
        return THREAD_LOCAL_SPAN_ID.get();
    }

    public static void setParentSpanId(String parentSpanId) {
        THREAD_LOCAL_PARENT_SPAN_ID.set(parentSpanId);
    }

    public static String getParentSpanId() {
        return THREAD_LOCAL_PARENT_SPAN_ID.get();
    }

    /**
     * Clears all thread-local context data to prevent memory leaks.
     */
    public static void clear() {
        THREAD_LOCAL_TAGS.remove();
        THREAD_LOCAL_TRACE_ID.remove();
        THREAD_LOCAL_SPAN_ID.remove();
        THREAD_LOCAL_PARENT_SPAN_ID.remove();
    }
}
