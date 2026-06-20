package in.maheshlangote.logdispatch;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Request Body Wrapping Tests")
class LogDispatchFilterRequestWrappingTest extends LogDispatchFilterBaseTest {

    @Test
    @DisplayName("Should wrap normal payloads to capture body")
    void shouldWrapNormalRequests() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/users");
        request.setContentType("application/json");
        request.setContent("hello".getBytes(StandardCharsets.UTF_8));

        HttpServletRequest actualRequest = requestSeenByFilterChain(request);

        assertThat(actualRequest).isInstanceOf(ContentCachingRequestWrapper.class);
    }

    @Test
    @DisplayName("Should skip wrapping for multipart uploads")
    void shouldNotWrapMultipartRequests() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/users");
        request.setContentType("multipart/form-data");

        HttpServletRequest actualRequest = requestSeenByFilterChain(request);

        assertThat(actualRequest).isNotInstanceOf(ContentCachingRequestWrapper.class);
    }

    @Test
    @DisplayName("Should skip wrapping for massive payloads (>32KB)")
    void shouldNotWrapLargePayloadRequests() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/users");
        request.setContentType("application/json");
        request.setContent(new byte[33 * 1024]);

        HttpServletRequest actualRequest = requestSeenByFilterChain(request);

        assertThat(actualRequest).isNotInstanceOf(ContentCachingRequestWrapper.class);
    }

    @Test
    @DisplayName("Should wrap exactly at the 32KB boundary")
    void shouldWrapPayloadAtExactly32KbBoundary() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/users");
        request.setContentType("application/json");
        request.setContent(new byte[32 * 1024]);

        HttpServletRequest actualRequest = requestSeenByFilterChain(request);

        assertThat(actualRequest).isInstanceOf(ContentCachingRequestWrapper.class);
    }

    @Test
    @DisplayName("Should skip wrapping GET requests with no body")
    void shouldNotWrapGetRequestsWithoutBody() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/users");

        HttpServletRequest actualRequest = requestSeenByFilterChain(request);

        assertThat(actualRequest).isNotInstanceOf(ContentCachingRequestWrapper.class);
    }
}
