package in.maheshlangote.logdispatch;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class LogDispatchFilterTest {

    private static final String SERVER_URL = "http://localhost:8080";
    private static final String API_KEY = "test-api-key";

    private RestTemplate restTemplate;
    private LogDispatchFilter filter;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        filter = filterWith(List.of("authorization"), List.of());
    }

    @Test
    void shouldDispatchToApmFor4xxResponse() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chainWithStatus(404));

        Map<String, Object> payload = dispatchedPayload();
        assertThat(payload).containsEntry("statusCode", 404);
        assertThat(payload).containsEntry("severity", "WARNING");
        assertThat(payload).containsEntry("errorPath", "/api/users");
    }

    @Test
    void shouldDispatchToApmFor5xxResponse() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chainWithStatus(500));

        Map<String, Object> payload = dispatchedPayload();
        assertThat(payload).containsEntry("statusCode", 500);
        assertThat(payload).containsEntry("severity", "CRITICAL");
    }

    @Test
    void shouldNotDispatchToApmFor2xxResponse() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chainWithStatus(204));

        verifyNoApmDispatch();
    }

    @Test
    void shouldNotDispatchToApmFor3xxResponse() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chainWithStatus(302));

        verifyNoApmDispatch();
    }

    @Test
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
    void shouldPassNonMaskedHeaderAsIs() throws Exception {
        MockHttpServletRequest request = request("GET", "/secure");
        request.addHeader("X-User", "naman");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chainWithStatus(401));

        Map<String, String> headers = dispatchedHeaders();
        assertThat(headers).containsEntry("X-User", "naman");
    }

    @Test
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
    void shouldHandleEmptyMaskedHeadersWithoutMasking() throws Exception {
        filter = filterWith(List.of(), List.of());
        MockHttpServletRequest request = request("GET", "/secure");
        request.addHeader("Authorization", "Bearer secret-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chainWithStatus(401));

        Map<String, String> headers = dispatchedHeaders();
        assertThat(headers).containsEntry("Authorization", "Bearer secret-token");
    }

    @Test
    void shouldWrapNormalRequests() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/users");
        request.setContentType("application/json");
        request.setContent("hello".getBytes(StandardCharsets.UTF_8));

        HttpServletRequest actualRequest = requestSeenByFilterChain(request);

        assertThat(actualRequest).isInstanceOf(ContentCachingRequestWrapper.class);
    }

    @Test
    void shouldNotWrapMultipartRequests() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/users");
        request.setContentType("multipart/form-data");

        HttpServletRequest actualRequest = requestSeenByFilterChain(request);

        assertThat(actualRequest).isNotInstanceOf(ContentCachingRequestWrapper.class);
    }

    @Test
    void shouldNotWrapLargePayloadRequests() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/users");
        request.setContentType("application/json");
        request.setContent(new byte[33 * 1024]);

        HttpServletRequest actualRequest = requestSeenByFilterChain(request);

        assertThat(actualRequest).isNotInstanceOf(ContentCachingRequestWrapper.class);
    }

    @Test
    void shouldWrapPayloadAtExactly32KbBoundary() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/users");
        request.setContentType("application/json");
        request.setContent(new byte[32 * 1024]);

        HttpServletRequest actualRequest = requestSeenByFilterChain(request);

        assertThat(actualRequest).isInstanceOf(ContentCachingRequestWrapper.class);
    }

    @Test
    void shouldNotWrapGetRequestsWithoutBody() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/users");

        HttpServletRequest actualRequest = requestSeenByFilterChain(request);

        assertThat(actualRequest).isNotInstanceOf(ContentCachingRequestWrapper.class);
    }

    @Test
    void shouldSkipApmForExactExcludedPath() throws Exception {
        filter = filterWith(List.of("authorization"), List.of("/health"));
        MockHttpServletRequest request = request("GET", "/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chainWithStatus(500));

        assertThat(response.getStatus()).isEqualTo(500);
        verifyNoApmDispatch();
    }

    @Test
    void shouldSkipApmForWildcardExcludedPath() throws Exception {
        filter = filterWith(List.of("authorization"), List.of("/actuator/**"));
        MockHttpServletRequest request = request("GET", "/actuator/metrics");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chainWithStatus(503));

        assertThat(response.getStatus()).isEqualTo(503);
        verifyNoApmDispatch();
    }

    @Test
    void shouldProcessNonExcludedPathNormally() throws Exception {
        filter = filterWith(List.of("authorization"), List.of("/health", "/actuator/**"));
        MockHttpServletRequest request = request("GET", "/api/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chainWithStatus(500));

        Map<String, Object> payload = dispatchedPayload();
        assertThat(payload).containsEntry("errorPath", "/api/users");
    }

    @Test
    void shouldProcessAllPathsWhenExcludeListIsEmpty() throws Exception {
        filter = filterWith(List.of("authorization"), List.of());
        MockHttpServletRequest request = request("GET", "/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chainWithStatus(500));

        Map<String, Object> payload = dispatchedPayload();
        assertThat(payload).containsEntry("errorPath", "/health");
    }

    @Test
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

    private LogDispatchFilter filterWith(List<String> maskedHeaders, List<String> excludePaths) {
        return new LogDispatchFilter(SERVER_URL, API_KEY, maskedHeaders, excludePaths, restTemplate, Runnable::run);
    }

    private static MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(method);
        request.setRequestURI(path);
        return request;
    }

    private static FilterChain chainWithStatus(int status) {
        return (request, response) -> ((HttpServletResponse) response).setStatus(status);
    }

    private HttpServletRequest requestSeenByFilterChain(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        ArgumentCaptor<HttpServletRequest> requestCaptor = ArgumentCaptor.forClass(HttpServletRequest.class);
        verify(filterChain).doFilter(requestCaptor.capture(), eq(response));
        return requestCaptor.getValue();
    }

    private void verifyNoApmDispatch() {
        verify(restTemplate, never()).postForEntity(anyString(), any(), eq(String.class));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dispatchedPayload() {
        ArgumentCaptor<Object> requestCaptor = ArgumentCaptor.forClass(Object.class);
        verify(restTemplate).postForEntity(eq(SERVER_URL), requestCaptor.capture(), eq(String.class));

        HttpEntity<?> entity = (HttpEntity<?>) requestCaptor.getValue();
        assertThat(entity.getHeaders().getFirst("X-API-KEY")).isEqualTo(API_KEY);
        return (Map<String, Object>) entity.getBody();
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> dispatchedHeaders() {
        Map<String, Object> payload = dispatchedPayload();
        Map<String, Object> inputInformation = (Map<String, Object>) payload.get("inputInformation");
        return (Map<String, String>) inputInformation.get("headers");
    }
}
