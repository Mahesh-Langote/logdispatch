package in.maheshlangote.logdispatch.config;

import in.maheshlangote.logdispatch.LogDispatchAspect;
import in.maheshlangote.logdispatch.LogDispatchFilter;
import in.maheshlangote.logdispatch.LogDispatchHealthController;
import in.maheshlangote.logdispatch.LogDispatchLogbackAppender;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
                properties.getTimeoutMs(),
                properties.getMaxStackFrames()
        ));
        registrationBean.addUrlPatterns("/*");
        // Use Highest Precedence to ensure it wraps everything including security filters
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registrationBean;
    }

    /**
     * Creates and attaches the Logback appender to capture developer debug logs during HTTP requests.
     *
     * @param properties LogDispatch configuration properties
     * @return a configured {@link LogDispatchLogbackAppender}.
     */
    @Bean
    @ConditionalOnClass(name = "ch.qos.logback.classic.LoggerContext")
    @ConditionalOnProperty(
            prefix = "logdispatch.logs",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public LogDispatchLogbackAppender logDispatchLogbackAppender(LogDispatchProperties properties) {
        LogDispatchLogbackAppender appender = new LogDispatchLogbackAppender();
        appender.setMaxEntries(properties.getLogs().getMaxEntries());
        appender.setMinLevel(properties.getLogs().getMinLevel());
        appender.setExcludeLoggers(properties.getLogs().getExcludeLoggers());
        appender.setIncludeLoggers(properties.getLogs().getIncludeLoggers());

        org.slf4j.ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        if (factory instanceof ch.qos.logback.classic.LoggerContext loggerContext) {
            appender.setContext(loggerContext);
            appender.start();
            ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
            rootLogger.addAppender(appender);
        }

        return appender;
    }

    /**
     * Creates and exposes the {@link in.maheshlangote.logdispatch.LogDispatchHealthController} bean.
     * This controller provides a lightweight health endpoint for the APM server to poll.
     * Registration is skipped entirely when {@code logdispatch.health.enabled=false},
     * so the endpoint does not exist rather than responding with a "disabled" status.
     *
     * @param properties LogDispatch configuration properties
     * @return a fully configured {@link in.maheshlangote.logdispatch.LogDispatchHealthController}.
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "logdispatch.health",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public LogDispatchHealthController logDispatchHealthController(LogDispatchProperties properties) {
        return new LogDispatchHealthController(properties.isEnabled());
    }
}