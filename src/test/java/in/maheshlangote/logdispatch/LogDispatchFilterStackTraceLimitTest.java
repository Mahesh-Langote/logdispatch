package in.maheshlangote.logdispatch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Stack Trace Limit Tests")
class LogDispatchFilterStackTraceLimitTest extends LogDispatchFilterBaseTest {

    @Test
    @DisplayName("Should truncate stack traces to the configured frame limit")
    void shouldTruncateStackTraceToConfiguredFrameLimit() throws Exception {
        filter = new LogDispatchFilter(
                true,
                SERVER_URL,
                API_KEY,
                List.of(),
                List.of(),
                restTemplate,
                Runnable::run,
                3000,
                2
        );
        RuntimeException exception = new RuntimeException("boom");
        exception.setStackTrace(new StackTraceElement[] {
                frame("first"), frame("second"), frame("third"), frame("fourth")
        });
        MockHttpServletRequest request = request("GET", "/fail");
        request.setAttribute("logdispatch.exception", exception);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chainWithStatus(500));

        Map<String, Object> payload = dispatchedPayload();
        assertThat(payload.get("stackTrace").toString())
                .contains("first", "second", "... 2 more frames truncated")
                .doesNotContain("third", "fourth");
    }

    private static StackTraceElement frame(String method) {
        return new StackTraceElement("Example", method, "Example.java", 1);
    }
}
