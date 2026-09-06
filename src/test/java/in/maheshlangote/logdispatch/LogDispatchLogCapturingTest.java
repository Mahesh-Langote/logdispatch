package in.maheshlangote.logdispatch;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

class LogDispatchLogCapturingTest extends LogDispatchFilterBaseTest {

    private LogDispatchLogbackAppender appender;

    @BeforeEach
    void setUpAppender() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        appender = new LogDispatchLogbackAppender();
        appender.setContext(context);
        appender.setMinLevel("DEBUG");
        appender.setMaxEntries(50);
        appender.start();

        Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.DEBUG);
        root.addAppender(appender);
    }

    @AfterEach
    void tearDownAppender() {
        if (appender != null) {
            appender.stop();
        }
    }

    @Test
    void testExecutionLogsCapturedAndDispatchedOnError() throws ServletException, IOException {
        LogDispatchFilter localFilter = filterWith(List.of(), List.of());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(500);

        org.slf4j.Logger logger = LoggerFactory.getLogger(LogDispatchLogCapturingTest.class);

        FilterChain filterChain = (req, res) -> {
            logger.debug("Debug log message during request");
            logger.info("Info log message during request");
            logger.warn("Warn log message during request");
            logger.error("Error log message during request");
        };

        localFilter.doFilter(request, response, filterChain);

        // Verify async payload dispatch
        ArgumentCaptor<HttpEntity<LogDispatchPayload>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq(SERVER_URL), captor.capture(), eq(String.class));

        LogDispatchPayload payload = captor.getValue().getBody();
        assertNotNull(payload);
        assertNotNull(payload.executionLogs());
        assertFalse(payload.executionLogs().isEmpty());

        List<ExecutionLogEntry> logs = payload.executionLogs();
        assertTrue(logs.stream().anyMatch(l -> "DEBUG".equals(l.level()) && l.message().contains("Debug log message")));
        assertTrue(logs.stream().anyMatch(l -> "INFO".equals(l.level()) && l.message().contains("Info log message")));
        assertTrue(logs.stream().anyMatch(l -> "WARN".equals(l.level()) && l.message().contains("Warn log message")));
        assertTrue(logs.stream().anyMatch(l -> "ERROR".equals(l.level()) && l.message().contains("Error log message")));

        // Verify buffer was cleaned up after request
        assertFalse(LogDispatchLogBuffer.isInitialized());
    }

    @Test
    void testExcludeLoggersFiltersOutNoisyLogs() throws ServletException, IOException {
        appender.setExcludeLoggers(List.of("com.zaxxer.hikari"));

        LogDispatchFilter localFilter = filterWith(List.of(), List.of());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test-exclude");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(500);

        org.slf4j.Logger hikariLogger = LoggerFactory.getLogger("com.zaxxer.hikari.pool.PoolBase");
        org.slf4j.Logger devLogger = LoggerFactory.getLogger("com.musterdekho.service.UserService");

        FilterChain filterChain = (req, res) -> {
            hikariLogger.warn("HikariPool-1 - Failed to validate connection com.mysql.cj.jdbc.ConnectionImpl");
            devLogger.info("User details retrieved successfully");
        };

        localFilter.doFilter(request, response, filterChain);

        ArgumentCaptor<HttpEntity<LogDispatchPayload>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq(SERVER_URL), captor.capture(), eq(String.class));

        LogDispatchPayload payload = captor.getValue().getBody();
        assertNotNull(payload);
        List<ExecutionLogEntry> logs = payload.executionLogs();

        // Hikari log should be excluded
        assertFalse(logs.stream().anyMatch(l -> l.loggerName().contains("hikari")));
        // Developer log should be present
        assertTrue(logs.stream().anyMatch(l -> l.loggerName().contains("UserService") && l.message().contains("User details")));
    }

    @Test
    void testIncludeLoggersCapturesOnlyWhitelistedPackages() throws ServletException, IOException {
        appender.setIncludeLoggers(List.of("com.musterdekho"));

        LogDispatchFilter localFilter = filterWith(List.of(), List.of());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test-include");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(500);

        org.slf4j.Logger thirdPartyLogger = LoggerFactory.getLogger("org.apache.catalina.core.ContainerBase");
        org.slf4j.Logger appLogger = LoggerFactory.getLogger("com.musterdekho.controller.OrderController");

        FilterChain filterChain = (req, res) -> {
            thirdPartyLogger.info("Tomcat container initialized");
            appLogger.info("Processing order #123");
        };

        localFilter.doFilter(request, response, filterChain);

        ArgumentCaptor<HttpEntity<LogDispatchPayload>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq(SERVER_URL), captor.capture(), eq(String.class));

        LogDispatchPayload payload = captor.getValue().getBody();
        assertNotNull(payload);
        List<ExecutionLogEntry> logs = payload.executionLogs();

        assertFalse(logs.stream().anyMatch(l -> l.loggerName().contains("ContainerBase")));
        assertTrue(logs.stream().anyMatch(l -> l.loggerName().contains("OrderController") && l.message().contains("order #123")));
    }

    @Test
    void testCommaSeparatedMinLevelGracefulParsing() throws ServletException, IOException {
        appender.setMinLevel("DEBUG, INFO, WARN, ERROR");

        LogDispatchFilter localFilter = filterWith(List.of(), List.of());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test-minlevel");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(500);

        org.slf4j.Logger logger = LoggerFactory.getLogger(LogDispatchLogCapturingTest.class);

        FilterChain filterChain = (req, res) -> {
            logger.debug("Debug message should be captured");
            logger.info("Info message should be captured");
        };

        localFilter.doFilter(request, response, filterChain);

        ArgumentCaptor<HttpEntity<LogDispatchPayload>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq(SERVER_URL), captor.capture(), eq(String.class));

        LogDispatchPayload payload = captor.getValue().getBody();
        assertNotNull(payload);
        List<ExecutionLogEntry> logs = payload.executionLogs();

        assertTrue(logs.stream().anyMatch(l -> "DEBUG".equals(l.level())));
        assertTrue(logs.stream().anyMatch(l -> "INFO".equals(l.level())));
    }
}
