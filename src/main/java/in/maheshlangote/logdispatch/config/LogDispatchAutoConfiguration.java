package in.maheshlangote.logdispatch.config;

import in.maheshlangote.logdispatch.LogDispatchAspect;
import in.maheshlangote.logdispatch.LogDispatchFilter;
import in.maheshlangote.logdispatch.LogDispatchHealthController;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Auto-configuration class for LogDispatch.
 * <p>
 * This configuration automatically registers LogDispatch beans and passes the
 * {@code logdispatch.enabled} flag to each component so disabled mode can no-op.
 * 
 * @author Mahesh Langote
 * @version 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(LogDispatchProperties.class)
public class LogDispatchAutoConfiguration {

    /**
     * Default constructor for auto-configuration.
     */
    public LogDispatchAutoConfiguration() {
    }

    /**
     * Creates and exposes the {@link LogDispatchAspect} bean.
     *
     * @param properties LogDispatch configuration properties
     * @return a fully configured {@link LogDispatchAspect} ready to intercept exceptions.
     */
    @Bean
    public LogDispatchAspect logDispatchAspect(LogDispatchProperties properties) {
        return new LogDispatchAspect(properties.isEnabled());
    }

    /**
     * Creates and exposes the {@link in.maheshlangote.logdispatch.LogDispatchFilter} bean.
     * This filter catches filter-level exceptions (e.g. 403 Forbidden).
     *
     * @param properties LogDispatch configuration properties
     * @return a fully configured {@link in.maheshlangote.logdispatch.LogDispatchFilter}.
     */
    @Bean
    public FilterRegistrationBean<LogDispatchFilter> logDispatchFilterRegistration(LogDispatchProperties properties) {
        FilterRegistrationBean<LogDispatchFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new LogDispatchFilter(
                properties.isEnabled(),
                properties.getServerUrl(),
                properties.getApiKey(),
                properties.getMaskedHeaders(),
                properties.getExcludePaths(),
                properties.getTimeoutMs()
        ));
        registrationBean.addUrlPatterns("/*");
        // Use Highest Precedence to ensure it wraps everything including security filters
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registrationBean;
    }

    /**
     * Creates and exposes the {@link in.maheshlangote.logdispatch.LogDispatchHealthController} bean.
     * This controller provides a lightweight health endpoint for the APM server to poll.
     *
     * @param properties LogDispatch configuration properties
     * @return a fully configured {@link in.maheshlangote.logdispatch.LogDispatchHealthController}.
     */
    @Bean
    public LogDispatchHealthController logDispatchHealthController(LogDispatchProperties properties) {
        return new LogDispatchHealthController(properties.isEnabled());
    }
}
