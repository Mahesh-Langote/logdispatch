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

    public LogDispatchAutoConfiguration() {
    }

    @Bean
    public LogDispatchAspect logDispatchAspect(LogDispatchProperties properties) {
        return new LogDispatchAspect(properties.isEnabled());
    }

    @Bean
    public FilterRegistrationBean<LogDispatchFilter> logDispatchFilterRegistration(LogDispatchProperties properties) {
        FilterRegistrationBean<LogDispatchFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new LogDispatchFilter(
                properties.isEnabled(),
                properties.getServerUrl(),
                properties.getApiKey(),
                properties.getMaskedHeaders(),
                properties.getExcludePaths(),
                null,
                null,
                properties.getTimeoutMs(),
                properties.getMaxStackFrames(),
                properties.getDispatchMode(),
                properties.getSlowThresholdMs(),
                properties.isIncludeRequestIp()
        ));
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registrationBean;
    }

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
