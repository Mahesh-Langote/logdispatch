package in.maheshlangote.logdispatch;

/**
 * Represents a single captured application log event (e.g. log.debug, log.info, log.warn, log.error).
 */
public record ExecutionLogEntry(
        String timestamp,
        String level,
        String loggerName,
        String threadName,
        String message,
        String throwable
) {}
