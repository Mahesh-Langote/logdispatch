package in.maheshlangote.logdispatch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuration properties for the LogDispatch starter.
 */
@ConfigurationProperties(prefix = "logdispatch")
public class LogDispatchProperties {

    private boolean enabled = true;
    private Health health = new Health();
    private Logs logs = new Logs();
    private String serverUrl = "http://localhost:8081/api/v1/ingest/logs";
    private String apiKey = "default-key";
    private List<String> maskedHeaders = List.of();
    private List<String> excludePaths = List.of();
    private List<String> excludeMethods = List.of("OPTIONS");
    private boolean ignoreOptionsRequests = true;
    private int timeoutMs = 3000;
    private int maxStackFrames = 100;
    private DispatchMode dispatchMode = DispatchMode.ERRORS_AND_SLOW; // Default: ERRORS_AND_SLOW (Optimized APM mode)
    private int slowThresholdMs = 3000; // Default: 3000 ms (3 seconds)
    private boolean includeRequestIp = true;

    public Health getHealth() {
        return health;
    }

    public void setHealth(Health health) {
        this.health = health;
    }

    public Logs getLogs() {
        return logs;
    }

    public void setLogs(Logs logs) {
        this.logs = logs;
    }

    /**
     * Configuration for the LogDispatch health endpoint.
     */
    public static class Health {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * Configuration for LogDispatch developer execution log capturing.
     */
    public static class Logs {
        private boolean enabled = true;
        private int maxEntries = 50;
        private LogLevel minLevel = LogLevel.DEBUG;
        private List<String> excludeLoggers = List.of();
        private List<String> includeLoggers = List.of();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxEntries() {
            return maxEntries;
        }

        public void setMaxEntries(int maxEntries) {
            this.maxEntries = maxEntries;
        }

        public LogLevel getMinLevel() {
            return minLevel;
        }

        public void setMinLevel(LogLevel minLevel) {
            this.minLevel = minLevel != null ? minLevel : LogLevel.DEBUG;
        }

        public List<String> getExcludeLoggers() {
            return excludeLoggers;
        }

        public void setExcludeLoggers(List<String> excludeLoggers) {
            this.excludeLoggers = excludeLoggers != null ? excludeLoggers : List.of();
        }

        public List<String> getIncludeLoggers() {
            return includeLoggers;
        }

        public void setIncludeLoggers(List<String> includeLoggers) {
            this.includeLoggers = includeLoggers != null ? includeLoggers : List.of();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public List<String> getMaskedHeaders() {
        return maskedHeaders;
    }

    public void setMaskedHeaders(List<String> maskedHeaders) {
        this.maskedHeaders = maskedHeaders;
    }

    public List<String> getExcludePaths() {
        return excludePaths;
    }

    public void setExcludePaths(List<String> excludePaths) {
        this.excludePaths = excludePaths;
    }

    public List<String> getExcludeMethods() {
        return excludeMethods;
    }

    public void setExcludeMethods(List<String> excludeMethods) {
        this.excludeMethods = excludeMethods != null ? excludeMethods : List.of();
    }

    public boolean isIgnoreOptionsRequests() {
        return ignoreOptionsRequests;
    }

    public void setIgnoreOptionsRequests(boolean ignoreOptionsRequests) {
        this.ignoreOptionsRequests = ignoreOptionsRequests;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getMaxStackFrames() {
        return maxStackFrames;
    }

    public void setMaxStackFrames(int maxStackFrames) {
        this.maxStackFrames = maxStackFrames;
    }

    public DispatchMode getDispatchMode() {
        return dispatchMode;
    }

    public void setDispatchMode(DispatchMode dispatchMode) {
        this.dispatchMode = dispatchMode != null ? dispatchMode : DispatchMode.ERRORS_AND_SLOW;
    }

    public int getSlowThresholdMs() {
        return slowThresholdMs;
    }

    public void setSlowThresholdMs(int slowThresholdMs) {
        this.slowThresholdMs = slowThresholdMs;
    }

    public boolean isIncludeRequestIp() {
        return includeRequestIp;
    }

    public void setIncludeRequestIp(boolean includeRequestIp) {
        this.includeRequestIp = includeRequestIp;
    }
}
