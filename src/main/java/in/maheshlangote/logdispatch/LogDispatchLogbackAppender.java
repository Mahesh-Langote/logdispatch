package in.maheshlangote.logdispatch;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;

import java.time.Instant;
import java.util.List;

/**
 * Logback appender that intercepts SLF4J log events and appends them
 * to the active request's ThreadLocal LogDispatchLogBuffer.
 */
public class LogDispatchLogbackAppender extends AppenderBase<ILoggingEvent> {

    private int maxEntries = 50;
    private String minLevel = "DEBUG";
    private List<String> excludeLoggers = List.of();
    private List<String> includeLoggers = List.of();

    public LogDispatchLogbackAppender() {
        setName("LogDispatchAppender");
    }

    public void setMaxEntries(int maxEntries) {
        this.maxEntries = maxEntries;
    }

    public void setMinLevel(String minLevel) {
        this.minLevel = minLevel;
    }

    public void setExcludeLoggers(List<String> excludeLoggers) {
        this.excludeLoggers = excludeLoggers != null ? excludeLoggers : List.of();
    }

    public void setIncludeLoggers(List<String> includeLoggers) {
        this.includeLoggers = includeLoggers != null ? includeLoggers : List.of();
    }

    private boolean isLoggerAllowed(String loggerName) {
        if (loggerName == null) {
            return true;
        }

        for (String exclude : excludeLoggers) {
            if (exclude != null && !exclude.isBlank() && (loggerName.startsWith(exclude.trim()) || loggerName.equals(exclude.trim()))) {
                return false;
            }
        }

        if (!includeLoggers.isEmpty()) {
            boolean matched = false;
            for (String include : includeLoggers) {
                if (include != null && !include.isBlank() && (loggerName.startsWith(include.trim()) || loggerName.equals(include.trim()))) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }

        return true;
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (!LogDispatchLogBuffer.isInitialized()) {
            return;
        }

        String loggerName = event.getLoggerName();
        if (!isLoggerAllowed(loggerName)) {
            return;
        }

        String timestamp = Instant.ofEpochMilli(event.getTimeStamp()).toString();
        String level = event.getLevel() != null ? event.getLevel().toString() : "INFO";
        String threadName = event.getThreadName();
        String message = event.getFormattedMessage();

        String throwableStr = null;
        IThrowableProxy throwableProxy = event.getThrowableProxy();
        if (throwableProxy != null) {
            throwableStr = ThrowableProxyUtil.asString(throwableProxy);
        }

        ExecutionLogEntry entry = new ExecutionLogEntry(
                timestamp,
                level,
                loggerName,
                threadName,
                message,
                throwableStr
        );

        LogDispatchLogBuffer.append(entry, maxEntries, minLevel);
    }
}
