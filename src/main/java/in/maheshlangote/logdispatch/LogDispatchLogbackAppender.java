package in.maheshlangote.logdispatch;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;

import in.maheshlangote.logdispatch.config.LogLevel;

import java.time.Instant;
import java.util.List;

/**
 * Logback appender that intercepts SLF4J log events and appends them
 * to the active request's execution buffer.
 */
public class LogDispatchLogbackAppender extends AppenderBase<ILoggingEvent> {

    private int maxEntries = 50;
    private LogLevel minLevel = LogLevel.TRACE;
    private List<String> excludeLoggers = List.of();
    private List<String> includeLoggers = List.of();

    public LogDispatchLogbackAppender() {
        setName("LogDispatchAppender");
    }

    public void setMaxEntries(int maxEntries) {
        this.maxEntries = maxEntries;
    }

    public LogLevel getMinLevel() {
        return minLevel;
    }

    public void setMinLevel(LogLevel minLevel) {
        this.minLevel = minLevel != null ? minLevel : LogLevel.TRACE;
    }

    public void setMinLevel(String minLevel) {
        this.minLevel = LogLevel.fromValue(minLevel);
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
        String message = event.getFormattedMessage();

        StringBuilder formatted = new StringBuilder();
        formatted.append(timestamp).append(" ")
                 .append(level).append(" ")
                 .append(loggerName).append(" : ")
                 .append(message);

        IThrowableProxy throwableProxy = event.getThrowableProxy();
        if (throwableProxy != null) {
            String throwableStr = ThrowableProxyUtil.asString(throwableProxy);
            if (throwableStr != null && !throwableStr.isEmpty()) {
                formatted.append("\n").append(throwableStr);
            }
        }

        LogDispatchLogBuffer.append(formatted.toString());
    }
}
