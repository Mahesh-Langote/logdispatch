package in.maheshlangote.logdispatch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Path Exclusion Configuration Tests")
class LogDispatchFilterPathExclusionTest extends LogDispatchFilterBaseTest {

    @Test
    @DisplayName("Should completely ignore exact excluded paths")
    void shouldSkipApmForExactExcludedPath() throws Exception {
        filter = filterWith(List.of("authorization"), List.of("/health"));
        MockHttpServletRequest request = request("GET", "/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chainWithStatus(500));

        assertThat(response.getStatus()).isEqualTo(500);
        verifyNoApmDispatch();
    }

    @Test
    @DisplayName("Should completely ignore wildcard excluded paths")
    void shouldSkipApmForWildcardExcludedPath() throws Exception {
        filter = filterWith(List.of("authorization"), List.of("/actuator/**"));
        MockHttpServletRequest request = request("GET", "/actuator/metrics");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chainWithStatus(503));

        assertThat(response.getStatus()).isEqualTo(503);
        verifyNoApmDispatch();
    }

    @Test
    @DisplayName("Should process paths not matching the exclusion list")
    void shouldProcessNonExcludedPathNormally() throws Exception {
        filter = filterWith(List.of("authorization"), List.of("/health", "/actuator/**"));
        MockHttpServletRequest request = request("GET", "/api/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chainWithStatus(500));

        Map<String, Object> payload = dispatchedPayload();
        assertThat(payload).containsEntry("errorPath", "/api/users");
    }

    @Test
    @DisplayName("Should process all paths if exclusion list is empty")
    void shouldProcessAllPathsWhenExcludeListIsEmpty() throws Exception {
        filter = filterWith(List.of("authorization"), List.of());
        MockHttpServletRequest request = request("GET", "/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chainWithStatus(500));

        Map<String, Object> payload = dispatchedPayload();
        assertThat(payload).containsEntry("errorPath", "/health");
    }
}
