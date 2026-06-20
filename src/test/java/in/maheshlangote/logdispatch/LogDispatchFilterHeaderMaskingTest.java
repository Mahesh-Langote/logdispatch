package in.maheshlangote.logdispatch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@DisplayName("Header Masking Logic Tests")
class LogDispatchFilterHeaderMaskingTest extends LogDispatchFilterBaseTest {

    @Test
    @DisplayName("Should mask configured sensitive headers")
    void shouldMaskConfiguredHeaderInApmPayload() throws Exception {
        MockHttpServletRequest request = request("GET", "/secure");
        request.addHeader("Authorization", "Bearer secret-token");
        request.addHeader("X-User", "naman");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chainWithStatus(401));

        Map<String, String> headers = dispatchedHeaders();
        assertThat(headers).containsEntry("Authorization", "********");
    }

    @Test
    @DisplayName("Should pass non-configured headers as is")
    void shouldPassNonMaskedHeaderAsIs() throws Exception {
        MockHttpServletRequest request = request("GET", "/secure");
        request.addHeader("X-User", "naman");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chainWithStatus(401));

        Map<String, String> headers = dispatchedHeaders();
        assertThat(headers).containsEntry("X-User", "naman");
    }

    @Test
    @DisplayName("Should mask headers case-insensitively")
    void shouldMaskHeadersCaseInsensitively() throws Exception {
        filter = filterWith(List.of("AUTHORIZATION"), List.of());
        MockHttpServletRequest request = request("GET", "/secure");
        request.addHeader("authorization", "Bearer secret-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chainWithStatus(401));

        Map<String, String> headers = dispatchedHeaders();
        assertThat(headers).containsEntry("authorization", "********");
    }

    @Test
    @DisplayName("Should handle null configured headers gracefully")
    void shouldHandleNullMaskedHeadersWithoutNpe() {
        filter = filterWith(null, List.of());
        MockHttpServletRequest request = request("GET", "/secure");
        request.addHeader("Authorization", "Bearer secret-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertDoesNotThrow(() -> filter.doFilter(request, response, chainWithStatus(401)));

        Map<String, String> headers = dispatchedHeaders();
        assertThat(headers).containsEntry("Authorization", "Bearer secret-token");
    }

    @Test
    @DisplayName("Should handle empty configured headers gracefully")
    void shouldHandleEmptyMaskedHeadersWithoutMasking() throws Exception {
        filter = filterWith(List.of(), List.of());
        MockHttpServletRequest request = request("GET", "/secure");
        request.addHeader("Authorization", "Bearer secret-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chainWithStatus(401));

        Map<String, String> headers = dispatchedHeaders();
        assertThat(headers).containsEntry("Authorization", "Bearer secret-token");
    }
}
