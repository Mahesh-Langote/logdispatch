package in.maheshlangote.logdispatch;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public abstract class LogDispatchFilterBaseTest {

    protected static final String SERVER_URL = "http://localhost:8080";
    protected static final String API_KEY = "test-api-key";

    protected RestTemplate restTemplate;
    protected LogDispatchFilter filter;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        filter = filterWith(List.of("authorization"), List.of());
    }

    protected LogDispatchFilter filterWith(List<String> maskedHeaders, List<String> excludePaths) {
        return new LogDispatchFilter(SERVER_URL, API_KEY, maskedHeaders, excludePaths, restTemplate, Runnable::run);
    }

    protected static MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(method);
        request.setRequestURI(path);
        return request;
    }

    protected static FilterChain chainWithStatus(int status) {
        return (request, response) -> ((HttpServletResponse) response).setStatus(status);
    }

    protected HttpServletRequest requestSeenByFilterChain(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        ArgumentCaptor<HttpServletRequest> requestCaptor = ArgumentCaptor.forClass(HttpServletRequest.class);
        verify(filterChain).doFilter(requestCaptor.capture(), eq(response));
        return requestCaptor.getValue();
    }

    protected void verifyNoApmDispatch() {
        verify(restTemplate, never()).postForEntity(anyString(), any(), eq(String.class));
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> dispatchedPayload() {
        ArgumentCaptor<Object> requestCaptor = ArgumentCaptor.forClass(Object.class);
        verify(restTemplate).postForEntity(eq(SERVER_URL), requestCaptor.capture(), eq(String.class));

        HttpEntity<?> entity = (HttpEntity<?>) requestCaptor.getValue();
        assertThat(entity.getHeaders().getFirst("X-API-KEY")).isEqualTo(API_KEY);
        
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.findAndRegisterModules();
        return mapper.convertValue(entity.getBody(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
    }

    @SuppressWarnings("unchecked")
    protected Map<String, String> dispatchedHeaders() {
        Map<String, Object> payload = dispatchedPayload();
        Map<String, Object> inputInformation = (Map<String, Object>) payload.get("inputInformation");
        return (Map<String, String>) inputInformation.get("headers");
    }
}
