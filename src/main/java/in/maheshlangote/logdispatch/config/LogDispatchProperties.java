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
    private int timeoutMs = 3000;
    private int maxStackFrames = 100;

    public Health getHealth(){
        return health;
    }

    public void setHealth(Health health){
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

        public void setEnabled(boolean enabled){
            this.enabled = enabled;
        }
    }

    /**
     * Configuration for LogDispatch developer execution log capturing.
     */
    public static class Logs {
        private boolean enabled = true;
        private int maxEntries = 50;
        private String minLevel = "DEBUG";
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

        public String getMinLevel() {
            return minLevel;
        }

        public void setMinLevel(String minLevel) {
            this.minLevel = minLevel;
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

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    /**
     * Returns the maximum number of stack frames included in an error payload.
     *
     * @return maximum stack frames
     */
    public int getMaxStackFrames() {
        return maxStackFrames;
    }

    /**
     * Sets the maximum number of stack frames included in an error payload.
     *
     * @param maxStackFrames maximum stack frames
     */
    public void setMaxStackFrames(int maxStackFrames) {
        this.maxStackFrames = maxStackFrames;
    }
}
