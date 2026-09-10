package in.maheshlangote.logdispatch;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class LogDispatchHealthControllerTest {

    @Test
    void shouldReturnDisabledStatusWhenLogDispatchIsDisabled() {

        LogDispatchHealthController controller = new LogDispatchHealthController(false);
        HttpServletRequest request = mock(HttpServletRequest.class);

        ResponseEntity<Map<String, Object>> response = controller.healthCheck(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("DISABLED", response.getBody().get("status"));
        assertEquals("LogDispatch is disabled.", response.getBody().get("message"));
        assertFalse(response.getBody().containsKey("uptimeSeconds"));
        verifyNoInteractions(request);
    }
}
