package in.maheshlangote.logdispatch;

import in.maheshlangote.logdispatch.config.DispatchMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@DisplayName("APM Dispatching Rules Tests")
class LogDispatchFilterApmDispatchingTest extends LogDispatchFilterBaseTest {

    @Test
    @DisplayName("Should dispatch 4xx responses as HTTP_FILTER_ERROR")
    void shouldDispatchToApmFor4xxResponse() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chainWithStatus(404));

        Map<String, Object> payload = dispatchedPayload();
        assertThat(payload).containsEntry("statusCode", 404);
        assertThat(payload).containsEntry("severity", "HTTP_FILTER_ERROR");
        assertThat(payload).containsEntry("errorPath", "/api/users");
        assertThat(payload).containsEntry("affectedFeature", "FilterRouting");
        assertThat(payload).containsEntry("errorType", "NotFound");
    }

    @Test
    @DisplayName("Should dispatch 401 responses as SECURITY")
    void shouldDispatchToApmForSecurityError() throws Exception {
        MockHttpServletRequest request = request("GET", "/secure");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chainWithStatus(401));

        Map<String, Object> payload = dispatchedPayload();
        assertThat(payload).containsEntry("statusCode", 401);
        assertThat(payload).containsEntry("severity", "SECURITY");
        assertThat(payload).containsEntry("affectedFeature", "FilterSecurity");
        assertThat(payload).containsEntry("errorType", "Unauthorized");
    }

    @Test
    @DisplayName("Should dispatch 5xx responses as HTTP_FILTER_ERROR")
    void shouldDispatchToApmFor5xxResponse() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chainWithStatus(500));

        Map<String, Object> payload = dispatchedPayload();
        assertThat(payload).containsEntry("statusCode", 500);
        assertThat(payload).containsEntry("severity", "HTTP_FILTER_ERROR");
        assertThat(payload).containsEntry("affectedFeature", "FilterInfrastructure");
        assertThat(payload).containsEntry("errorType", "InternalServerError");
    }

    @Test
    @DisplayName("Should extract Spring Security exception from request attribute for filter errors as SECURITY")
    void shouldExtractSpringSecurityExceptionFromRequestAttribute() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/protected");
        request.setAttribute("SPRING_SECURITY_LAST_EXCEPTION", new IllegalAccessException("Bad token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chainWithStatus(401));

        Map<String, Object> payload = dispatchedPayload();
        assertThat(payload).containsEntry("statusCode", 401);
        assertThat(payload).containsEntry("severity", "SECURITY");
        assertThat(payload).containsEntry("errorType", "IllegalAccessException");
        assertThat(payload).containsEntry("errorMessage", "Bad token");
        assertThat(payload).containsEntry("affectedFeature", "Filter/IllegalAccessException");
    }

    @Test
    @DisplayName("Should dispatch pre-controller / filter exceptions as HTTP_FILTER_ERROR when status is 4xx")
    void shouldDispatchPreControllerExceptionAsHttpFilterError() throws Exception {
        MockHttpServletRequest request = request("GET", "/sito/wp-includes/wlwmanifest.xml");
        request.setAttribute("logdispatch.exception", new RuntimeException("Header REQUEST-APP is required"));
        request.setAttribute("logdispatch.feature", "HeaderService");
        request.setAttribute("logdispatch.function", "getRequestApp");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chainWithStatus(404));

        Map<String, Object> payload = dispatchedPayload();
        assertThat(payload).containsEntry("statusCode", 404);
        assertThat(payload).containsEntry("severity", "HTTP_FILTER_ERROR");
        assertThat(payload).containsEntry("affectedFeature", "HeaderService");
        assertThat(payload).containsEntry("affectedFunction", "getRequestApp");
        assertThat(payload).containsEntry("errorType", "RuntimeException");
    }

    @Test
    @DisplayName("Should dispatch controller 4xx exceptions as WARNING")
    void shouldDispatchController4xxAsWarning() throws Exception {
        org.springframework.web.method.HandlerMethod handlerMethod = new org.springframework.web.method.HandlerMethod(
                new SampleController(),
                SampleController.class.getMethod("getUsers")
        );

        MockHttpServletRequest request = request("POST", "/api/users");
        request.setAttribute("org.springframework.web.servlet.HandlerMapping.bestMatchingHandler", handlerMethod);
        request.setAttribute("logdispatch.exception", new IllegalArgumentException("Invalid user"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chainWithStatus(400));

        Map<String, Object> payload = dispatchedPayload();
        assertThat(payload).containsEntry("statusCode", 400);
        assertThat(payload).containsEntry("severity", "WARNING");
    }

    @Test
    @DisplayName("Should dispatch controller 401 Unauthorized as SECURITY")
    void shouldDispatchController401AsSecurity() throws Exception {
        org.springframework.web.method.HandlerMethod handlerMethod = new org.springframework.web.method.HandlerMethod(
                new SampleController(),
                SampleController.class.getMethod("getUsers")
        );

        MockHttpServletRequest request = request("POST", "/api/auth/login");
        request.setAttribute("org.springframework.web.servlet.HandlerMapping.bestMatchingHandler", handlerMethod);
        request.setAttribute("logdispatch.exception", new RuntimeException("Bad credentials"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chainWithStatus(401));

        Map<String, Object> payload = dispatchedPayload();
        assertThat(payload).containsEntry("statusCode", 401);
        assertThat(payload).containsEntry("severity", "SECURITY");
    }

    @Test
    @DisplayName("Should dispatch controller 500 exceptions as CRITICAL")
    void shouldDispatchController500AsCritical() throws Exception {
        org.springframework.web.method.HandlerMethod handlerMethod = new org.springframework.web.method.HandlerMethod(
                new SampleController(),
                SampleController.class.getMethod("getUsers")
        );

        MockHttpServletRequest request = request("GET", "/api/users");
        request.setAttribute("org.springframework.web.servlet.HandlerMapping.bestMatchingHandler", handlerMethod);
        request.setAttribute("logdispatch.exception", new RuntimeException("Database error"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chainWithStatus(500));

        Map<String, Object> payload = dispatchedPayload();
        assertThat(payload).containsEntry("statusCode", 500);
        assertThat(payload).containsEntry("severity", "CRITICAL");
    }

    @Test
    @DisplayName("Should not dispatch 2xx successful responses in ERRORS_ONLY mode")
    void shouldNotDispatchToApmFor2xxResponseInErrorsOnlyMode() throws Exception {
        filter = filterWith(List.of(), List.of(), DispatchMode.ERRORS_ONLY);
        MockHttpServletRequest request = request("GET", "/api/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chainWithStatus(204));

        verifyNoApmDispatch();
    }

    @Test
    @DisplayName("Should not dispatch 3xx redirect responses in ERRORS_ONLY mode")
    void shouldNotDispatchToApmFor3xxResponseInErrorsOnlyMode() throws Exception {
        filter = filterWith(List.of(), List.of(), DispatchMode.ERRORS_ONLY);
        MockHttpServletRequest request = request("GET", "/api/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chainWithStatus(302));

        verifyNoApmDispatch();
    }

    @Test
    @DisplayName("Should gracefully continue if APM server is unreachable")
    void shouldCompleteOriginalResponseWhenApmServerIsUnreachable() {
        doThrow(new ResourceAccessException("Connection refused"))
                .when(restTemplate)
                .postForEntity(eq(SERVER_URL), any(), eq(String.class));
        MockHttpServletRequest request = request("GET", "/api/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertDoesNotThrow(() -> filter.doFilter(request, response, chainWithStatus(503)));

        assertThat(response.getStatus()).isEqualTo(503);
        verify(restTemplate).postForEntity(eq(SERVER_URL), any(), eq(String.class));
    }

    @Test
    @DisplayName("Should dynamically extract Controller and Method name from HandlerMethod for 2xx responses")
    void shouldExtractControllerAndMethodFromHandlerMethod() throws Exception {
        filter = filterWith(List.of(), List.of(), DispatchMode.ALL);
        MockHttpServletRequest request = request("GET", "/api/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        org.springframework.web.method.HandlerMethod handlerMethod = new org.springframework.web.method.HandlerMethod(
                new SampleController(),
                SampleController.class.getMethod("getUsers")
        );
        request.setAttribute("org.springframework.web.servlet.HandlerMapping.bestMatchingHandler", handlerMethod);

        filter.doFilter(request, response, chainWithStatus(200));

        Map<String, Object> payload = dispatchedPayload();
        assertThat(payload).containsEntry("affectedFeature", "SampleController");
        assertThat(payload).containsEntry("affectedFunction", "getUsers");
    }

    static class SampleController {
        public void getUsers() {}
    }
}
