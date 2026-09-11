package ee.example.itagent.api;

import ee.example.itagent.api.dto.ApiError;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .toList();
        return new ApiError("validation_failed", details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleUnreadable(HttpMessageNotReadableException ex) {
        return new ApiError("malformed_request", List.of("Request body is missing or not valid JSON."));
    }

    /**
     * Last-resort handler for anything AgentService/ChatClient throws (an OpenAI
     * outage, timeout, or a structured-output response the converter still can't
     * parse). Without this, such a failure propagated as a generic Spring error
     * body, breaking the AgentResponse contract every other path on this API
     * honours, and risked leaking exception detail to the client.
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiError handleUnexpected(Exception ex) {
        log.error("Unhandled exception answering a request", ex);
        return new ApiError("service_unavailable",
                List.of("Vabandame, teenus ei ole hetkel kättesaadav. Palun proovi hiljem uuesti."));
    }
}
