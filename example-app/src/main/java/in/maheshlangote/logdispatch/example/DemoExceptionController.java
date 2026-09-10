package in.maheshlangote.logdispatch.example;

import in.maheshlangote.logdispatch.annotation.LogDispatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/demo")
@LogDispatch(feature = "Example Demo Controller")
public class DemoExceptionController {

    private static final Logger log = LoggerFactory.getLogger(DemoExceptionController.class);

    @GetMapping("/null-pointer")
    public String throwNullPointerException() {
        log.info("Processing GET /api/demo/null-pointer");
        log.debug("Simulating null reference access...");
        String value = null;
        return value.toUpperCase();
    }

    @GetMapping("/illegal-argument")
    public String throwIllegalArgumentException() {
        log.info("Processing GET /api/demo/illegal-argument");
        log.warn("Validation failed: supplied demo value is invalid");
        throw new IllegalArgumentException("The supplied demo value is not valid.");
    }

    @GetMapping("/illegal-state")
    public String throwIllegalStateException() {
        log.info("Processing GET /api/demo/illegal-state");
        log.error("System state mismatch detected!");
        throw new IllegalStateException("The demo workflow is in an invalid state.");
    }

    @GetMapping("/annotated")
    @LogDispatch(api = "Annotated Demo Endpoint", function = "throwAnnotatedException", severity = in.maheshlangote.logdispatch.annotation.LogSeverity.CRITICAL)
    public String throwAnnotatedException() {
        log.info("Processing GET /api/demo/annotated");
        log.debug("Custom LogDispatch annotation demo endpoint invoked");
        throw new UnsupportedOperationException("This endpoint demonstrates custom LogDispatch metadata with CRITICAL severity.");
    }

    @PostMapping("/body")
    @LogDispatch(api = "Request Body Demo", function = "throwBodyException")
    public String throwRequestBodyException(@RequestBody Map<String, Object> body) {
        log.info("Processing POST /api/demo/body with payload keys: {}", body.keySet());
        log.debug("Validating request body structure...");
        throw new IllegalArgumentException("Received body keys: " + body.keySet());
    }
}
