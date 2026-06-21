package in.maheshlangote.logdispatch.example;

import in.maheshlangote.logdispatch.annotation.LogDispatch;
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

    @GetMapping("/null-pointer")
    public String throwNullPointerException() {
        String value = null;
        return value.toUpperCase();
    }

    @GetMapping("/illegal-argument")
    public String throwIllegalArgumentException() {
        throw new IllegalArgumentException("The supplied demo value is not valid.");
    }

    @GetMapping("/illegal-state")
    public String throwIllegalStateException() {
        throw new IllegalStateException("The demo workflow is in an invalid state.");
    }

    @GetMapping("/annotated")
    @LogDispatch(api = "Annotated Demo Endpoint", function = "throwAnnotatedException")
    public String throwAnnotatedException() {
        throw new UnsupportedOperationException("This endpoint demonstrates custom LogDispatch metadata.");
    }

    @PostMapping("/body")
    @LogDispatch(api = "Request Body Demo", function = "throwBodyException")
    public String throwRequestBodyException(@RequestBody Map<String, Object> body) {
        throw new IllegalArgumentException("Received body keys: " + body.keySet());
    }
}
