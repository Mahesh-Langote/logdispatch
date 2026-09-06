package in.maheshlangote.logdispatch;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;

import java.time.Instant;

/**
 * Logback appender that intercepts SLF4J log events and appends them
 * to the active request's ThreadLocal LogDispatchLogBuffer.
 */
public class LogDispatchLogbackAppender extends AppenderBase<ILoggingEvent> {

    private int maxEntries = 50;
    private String minLevel = "DEBUG";

    public LogDispatchLogbackAppender() {
        setName("LogDispatchAppender");
    }

    public void setMaxEntries(int maxEntries) {
        this.maxEntries = maxEntries;
    }

    public void setMinLevel(String minLevel) {
        this.minLevel = minLevel;
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (!LogDispatchLogBuffer.isInitialized()) {
            return;
        }

        String timestamp = Instant.ofEpochMilli(event.getTimeStamp()).toString();
        String level = event.getLevel() != null ? event.getLevel().toString() : "INFO";
        String loggerName = event.getLoggerName();
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
