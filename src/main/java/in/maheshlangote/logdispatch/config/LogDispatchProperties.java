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
    private String serverUrl = "http://localhost:8081/api/v1/ingest/logs";
    private String apiKey = "default-key";
    private List<String> maskedHeaders = List.of();
    private List<String> excludePaths = List.of();
    private int timeoutMs = 3000;
    private int maxStackFrames = 100;

    /**
     * Returns whether LogDispatch is enabled.
     *
     * @return {@code true} when LogDispatch should inspect and dispatch errors
     */

    public Health getHealth(){
        return health;
    }

    public void setHealth(Health health){
        this.health = health;
    }

    /**
 * Configuration for the LogDispatch health endpoint.
 * */
    public static class Health {
        private boolean enabled = true;
        /**
     * Returns whether the LogDispatch health endpoint is enabled.
     *
     * @return {@code true} when the /logdispatch/health endpoint should be registered
     */
       public boolean isEnabled() {
        return enabled;
       }

       public void setEnabled(boolean enabled){
        this.enabled = enabled;
       }
    }



    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether LogDispatch is enabled.
     *
     * @param enabled {@code true} to enable LogDispatch, {@code false} to no-op
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns the APM ingest endpoint URL.
     *
     * @return the configured server URL
     */
    public String getServerUrl() {
        return serverUrl;
    }

    /**
     * Sets the APM ingest endpoint URL.
     *
     * @param serverUrl the server URL
     */
    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    /**
     * Returns the API key used for APM requests.
     *
     * @return the configured API key
     */
    public String getApiKey() {
        return apiKey;
    }

    /**
     * Sets the API key used for APM requests.
     *
     * @param apiKey the API key
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * Returns headers that should be masked before dispatch.
     *
     * @return header names to mask
     */
    public List<String> getMaskedHeaders() {
        return maskedHeaders;
    }

    /**
     * Sets headers that should be masked before dispatch.
     *
     * @param maskedHeaders header names to mask
     */
    public void setMaskedHeaders(List<String> maskedHeaders) {
        this.maskedHeaders = maskedHeaders;
    }

    /**
     * Returns URI patterns that should be excluded from dispatch.
     *
     * @return URI patterns to exclude
     */
    public List<String> getExcludePaths() {
        return excludePaths;
    }

    /**
     * Sets URI patterns that should be excluded from dispatch.
     *
     * @param excludePaths URI patterns to exclude
     */
    public void setExcludePaths(List<String> excludePaths) {
        this.excludePaths = excludePaths;
    }

    /**
     * Returns the HTTP connection and read timeout in milliseconds.
     *
     * @return timeout in milliseconds
     */
    public int getTimeoutMs() {
        return timeoutMs;
    }

    /**
     * Sets the HTTP connection and read timeout in milliseconds.
     *
     * @param timeoutMs timeout in milliseconds
     */
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
