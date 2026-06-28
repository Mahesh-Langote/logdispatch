package in.maheshlangote.logdispatch;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("Disabled LogDispatch Filter Tests")
class LogDispatchFilterDisabledTest extends LogDispatchFilterBaseTest {

    @Test
    @DisplayName("Should pass through without APM logic when disabled")
    void shouldPassThroughWithoutApmLogicWhenDisabled() throws Exception {
        LogDispatchFilter disabledFilter = new LogDispatchFilter(
                false,
                SERVER_URL,
                API_KEY,
                List.of("authorization"),
                List.of(),
                restTemplate,
                Runnable::run,
                3000
        );

        MockHttpServletRequest request = request("POST", "/test");
        request.setContentType("application/json");
        request.setContent("hello".getBytes());

        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(500);

        FilterChain filterChain = mock(FilterChain.class);

        disabledFilter.doFilter(request, response, filterChain);

        ArgumentCaptor<HttpServletRequest> requestCaptor = ArgumentCaptor.forClass(HttpServletRequest.class);
        verify(filterChain).doFilter(requestCaptor.capture(), eq(response));
        assertThat(requestCaptor.getValue()).isSameAs(request);
        verifyNoApmDispatch();
    }
}
