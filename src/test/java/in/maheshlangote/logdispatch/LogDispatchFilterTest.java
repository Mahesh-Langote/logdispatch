package in.maheshlangote.logdispatch;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LogDispatchFilterTest {

    private LogDispatchFilter filter;

    @BeforeEach
    void setUp() {
        filter = new LogDispatchFilter(
                "http://localhost:8080",
                "test-api-key",
                java.util.Collections.singletonList("authorization"),
                java.util.Collections.emptyList()
        );
    }

    @Test
    void shouldWrapNormalRequests() throws Exception {

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setContentType("application/json");
        request.setContent("hello".getBytes());

        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        ArgumentCaptor<HttpServletRequest> captor =
                ArgumentCaptor.forClass(HttpServletRequest.class);

        verify(filterChain).doFilter(captor.capture(), eq(response));

        HttpServletRequest wrappedRequest = captor.getValue();

        assertTrue(wrappedRequest instanceof ContentCachingRequestWrapper);
    }

    @Test
    void shouldNotWrapMultipartRequests() throws Exception {

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setContentType("multipart/form-data");

        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        ArgumentCaptor<HttpServletRequest> captor =
                ArgumentCaptor.forClass(HttpServletRequest.class);

        verify(filterChain).doFilter(captor.capture(), eq(response));

        HttpServletRequest actualRequest = captor.getValue();

        assertFalse(actualRequest instanceof ContentCachingRequestWrapper);
    }

    @Test
    void shouldNotWrapLargePayloadRequests() throws Exception {

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setContentType("application/json");

        byte[] largeContent = new byte[33 * 1024];
        request.setContent(largeContent);

        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        ArgumentCaptor<HttpServletRequest> captor =
                ArgumentCaptor.forClass(HttpServletRequest.class);

        verify(filterChain).doFilter(captor.capture(), eq(response));

        HttpServletRequest actualRequest = captor.getValue();

        assertFalse(actualRequest instanceof ContentCachingRequestWrapper);
    }

    @Test
    void shouldProcessRequestsWithSensitiveHeaders() throws Exception {

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/test");

        request.addHeader("Authorization", "Bearer secret-token");
        request.addHeader("X-User", "naman");

        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(401);

        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(any(), eq(response));
    }
}