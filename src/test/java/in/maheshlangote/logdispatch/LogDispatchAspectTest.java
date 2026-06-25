package in.maheshlangote.logdispatch;

import in.maheshlangote.logdispatch.annotation.LogDispatch;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("LogDispatch Aspect Tests")
class LogDispatchAspectTest {

    private LogDispatchAspect aspect;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        aspect = new LogDispatchAspect();
        request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/api/test");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("Should populate request attributes with defaults when no annotation is present")
    void shouldPopulateDefaults() throws Exception {
        JoinPoint joinPoint = mock(JoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);

        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getDeclaringType()).thenReturn(DummyController.class);
        when(signature.getName()).thenReturn("doSomething");

        Method method = DummyController.class.getMethod("doSomething");
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getTarget()).thenReturn(new DummyController());

        RuntimeException ex = new RuntimeException("Test exception");

        aspect.handleControllerException(joinPoint, ex);

        assertThat(request.getAttribute("logdispatch.handled")).isEqualTo(true);
        assertThat(request.getAttribute("logdispatch.exception")).isEqualTo(ex);
        assertThat(request.getAttribute("logdispatch.feature")).isEqualTo("DummyController");
        assertThat(request.getAttribute("logdispatch.function")).isEqualTo("doSomething");
        assertThat(request.getAttribute("logdispatch.api")).isEqualTo("/api/test");
    }

    @Test
    @DisplayName("Should populate request attributes from method annotation")
    void shouldPopulateFromMethodAnnotation() throws Exception {
        JoinPoint joinPoint = mock(JoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);

        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getDeclaringType()).thenReturn(DummyController.class);
        when(signature.getName()).thenReturn("doSomethingAnnotated");

        Method method = DummyController.class.getMethod("doSomethingAnnotated");
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getTarget()).thenReturn(new DummyController());

        RuntimeException ex = new RuntimeException("Test exception");

        aspect.handleControllerException(joinPoint, ex);

        assertThat(request.getAttribute("logdispatch.feature")).isEqualTo("CustomFeature");
        assertThat(request.getAttribute("logdispatch.function")).isEqualTo("customFunction");
        assertThat(request.getAttribute("logdispatch.api")).isEqualTo("/custom/api");
    }

    @Test
    @DisplayName("Should gracefully handle null request context")
    void shouldHandleNullRequestContextGracefully() throws Exception {
        RequestContextHolder.resetRequestAttributes(); // Remove context

        JoinPoint joinPoint = mock(JoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);

        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getDeclaringType()).thenReturn(DummyController.class);
        when(signature.getName()).thenReturn("doSomething");

        Method method = DummyController.class.getMethod("doSomething");
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getTarget()).thenReturn(new DummyController());

        RuntimeException ex = new RuntimeException("Test exception");

        // Should not throw NPE
        aspect.handleControllerException(joinPoint, ex);
    }

    @Test
    @DisplayName("Should no-op when LogDispatch is disabled")
    void shouldNoOpWhenDisabled() {
        LogDispatchAspect disabledAspect = new LogDispatchAspect(false);
        JoinPoint joinPoint = mock(JoinPoint.class);

        disabledAspect.handleControllerException(joinPoint, new RuntimeException("Test exception"));

        assertThat(request.getAttribute("logdispatch.handled")).isNull();
        assertThat(request.getAttribute("logdispatch.exception")).isNull();
        assertThat(request.getAttribute("logdispatch.feature")).isNull();
        assertThat(request.getAttribute("logdispatch.api")).isNull();
        assertThat(request.getAttribute("logdispatch.function")).isNull();
        verifyNoInteractions(joinPoint);
    }

    // Dummy controller for reflection
    static class DummyController {
        public void doSomething() {}

        @LogDispatch(feature = "CustomFeature", api = "/custom/api", function = "customFunction")
        public void doSomethingAnnotated() {}
    }
}
