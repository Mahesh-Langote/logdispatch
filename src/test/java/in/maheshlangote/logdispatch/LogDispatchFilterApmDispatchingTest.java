package in.maheshlangote.logdispatch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.client.ResourceAccessException;

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
    @DisplayName("Should dispatch 4xx responses as SECURITY")
    void shouldDispatchToApmFor4xxResponse() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chainWithStatus(404));

        Map<String, Object> payload = dispatchedPayload();
        assertThat(payload).containsEntry("statusCode", 404);
        assertThat(payload).containsEntry("severity", "SECURITY");
        assertThat(payload).containsEntry("errorPath", "/api/users");
        assertThat(payload).containsEntry("affectedFeature", "FilterSecurity/Routing");
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
        assertThat(payload).containsEntry("affectedFeature", "FilterSecurity/Routing");
    }

    @Test
    @DisplayName("Should dispatch 5xx responses as SECURITY")
    void shouldDispatchToApmFor5xxResponse() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chainWithStatus(500));

        Map<String, Object> payload = dispatchedPayload();
        assertThat(payload).containsEntry("statusCode", 500);
        assertThat(payload).containsEntry("severity", "SECURITY");
        assertThat(payload).containsEntry("affectedFeature", "FilterSecurity/Routing");
    }

    @Test
    @DisplayName("Should not dispatch 2xx successful responses")
    void shouldNotDispatchToApmFor2xxResponse() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chainWithStatus(204));

        verifyNoApmDispatch();
    }

    @Test
    @DisplayName("Should not dispatch 3xx redirect responses")
    void shouldNotDispatchToApmFor3xxResponse() throws Exception {
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
}
