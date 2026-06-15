package in.maheshlangote.logdispatch.config;

import in.maheshlangote.logdispatch.LogDispatchAspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration class for LogDispatch.
 * <p>
 * This configuration automatically registers the {@link LogDispatchAspect} bean
 * if the {@code logdispatch.enabled} property is set to true (or is missing).
 * It dynamically injects the server URL and API key from the application properties.
 * 
 * @author Mahesh Langote
 * @version 1.0.0
 */
@AutoConfiguration
public class LogDispatchAutoConfiguration {

    /**
     * Default constructor for auto-configuration.
     */
    public LogDispatchAutoConfiguration() {
    }

    @Value("${logdispatch.server-url:http://localhost:8081/api/v1/ingest/logs}")
    private String serverUrl;

    @Value("${logdispatch.api-key:default-key}")
    private String apiKey;

    @Value("${logdispatch.masked-headers:}")
    private java.util.List<String> maskedHeaders;

    @Value("${logdispatch.exclude-paths:}")
    private java.util.List<String> excludePaths;

    /**
     * Creates and exposes the {@link LogDispatchAspect} bean.
     *
     * @return a fully configured {@link LogDispatchAspect} ready to intercept exceptions.
     */
    @Bean
    @ConditionalOnProperty(name = "logdispatch.enabled", havingValue = "true", matchIfMissing = true)
    public LogDispatchAspect logDispatchAspect() {
        return new LogDispatchAspect();
    }

    /**
     * Creates and exposes the {@link in.maheshlangote.logdispatch.LogDispatchFilter} bean.
     * This filter catches filter-level exceptions (e.g. 403 Forbidden).
     *
     * @return a fully configured {@link in.maheshlangote.logdispatch.LogDispatchFilter}.
     */
    @Bean
    @ConditionalOnProperty(name = "logdispatch.enabled", havingValue = "true", matchIfMissing = true)
    public org.springframework.boot.web.servlet.FilterRegistrationBean<in.maheshlangote.logdispatch.LogDispatchFilter> logDispatchFilterRegistration() {
        org.springframework.boot.web.servlet.FilterRegistrationBean<in.maheshlangote.logdispatch.LogDispatchFilter> registrationBean = new org.springframework.boot.web.servlet.FilterRegistrationBean<>();
        registrationBean.setFilter(new in.maheshlangote.logdispatch.LogDispatchFilter(serverUrl, apiKey, maskedHeaders, excludePaths));
        registrationBean.addUrlPatterns("/*");
        // Use Highest Precedence to ensure it wraps everything including security filters
        registrationBean.setOrder(org.springframework.core.Ordered.HIGHEST_PRECEDENCE);
        return registrationBean;
    }

    /**
     * Creates and exposes the {@link in.maheshlangote.logdispatch.LogDispatchHealthController} bean.
     * This controller provides a lightweight health endpoint for the APM server to poll.
     *
     * @return a fully configured {@link in.maheshlangote.logdispatch.LogDispatchHealthController}.
     */
    @Bean
    @ConditionalOnProperty(name = "logdispatch.enabled", havingValue = "true", matchIfMissing = true)
    public in.maheshlangote.logdispatch.LogDispatchHealthController logDispatchHealthController() {
        return new in.maheshlangote.logdispatch.LogDispatchHealthController();
    }
}
