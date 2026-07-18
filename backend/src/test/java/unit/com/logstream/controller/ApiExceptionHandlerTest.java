package unit.com.logstream.controller;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import com.logstream.exception.ApiExceptionHandler;
import com.logstream.exception.InvalidLogEventException;
import com.logstream.generated.model.ErrorResponse;

public class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();
    private final MockHttpServletRequest request = ingestRequest();

    private static MockHttpServletRequest ingestRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/log-events");
        request.setRequestURI("/api/v1/log-events");
        return request;
    }

    @SuppressWarnings("unused")
    private void sampleMethod(String xIngestionToken) {
        // used only to build MethodParameter instances for exception construction
    }

    private MethodParameter sampleParameter() throws NoSuchMethodException {
        return new MethodParameter(
                getClass().getDeclaredMethod("sampleMethod", String.class), 0);
    }

    @Test
    void invalidLogEventMapsToBadRequestWithDocumentedBody() {
        ResponseEntity<ErrorResponse> response = handler.handleInvalidLogEvent(
                new InvalidLogEventException("Log event must include a message field."), request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(400, body.getStatus());
        assertEquals("INVALID_LOG_EVENT", body.getCode());
        assertTrue(body.getMessage().contains("message field"));
        assertEquals("/api/v1/log-events", body.getPath());
    }

    @Test
    void malformedJsonMapsToBadRequest() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "JSON parse error: Unexpected character", new MockHttpInputMessage(new byte[0]));

        ResponseEntity<ErrorResponse> response = handler.handleUnreadableBody(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("MALFORMED_JSON", response.getBody().getCode());
        assertTrue(response.getBody().getMessage().toLowerCase().contains("json"));
    }

    @Test
    void beanValidationFailureListsFieldErrors() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "logEventRequest");
        bindingResult.addError(new FieldError("logEventRequest", "message", "must not be blank"));
        bindingResult.addError(new FieldError("logEventRequest", "id", "size must be between 1 and 150"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(sampleParameter(), bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleMethodArgumentNotValid(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("VALIDATION_FAILED", response.getBody().getCode());
        assertTrue(response.getBody().getMessage().contains("message: must not be blank"));
        assertTrue(response.getBody().getMessage().contains("id: size must be between 1 and 150"));
    }

    @Test
    void handlerMethodValidationFailureMapsToBadRequest() {
        FieldError error = new FieldError("requestBody", "requestBody", "size must be between 1 and 100");
        HandlerMethodValidationException ex = mock(HandlerMethodValidationException.class);
        when(ex.getAllErrors()).thenAnswer(invocation -> List.of(error));

        ResponseEntity<ErrorResponse> response = handler.handleHandlerMethodValidation(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("VALIDATION_FAILED", response.getBody().getCode());
        assertTrue(response.getBody().getMessage().contains("size must be between 1 and 100"));
    }

    @Test
    void missingIngestionTokenHeaderMapsToUnauthorized() throws NoSuchMethodException {
        MissingRequestHeaderException ex =
                new MissingRequestHeaderException("X-Ingestion-Token", sampleParameter());

        ResponseEntity<ErrorResponse> response = handler.handleMissingHeader(ex, request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("UNAUTHORIZED", response.getBody().getCode());
        assertTrue(response.getBody().getMessage().contains("ingestion token"));
    }

    @Test
    void missingOtherHeaderMapsToBadRequest() throws NoSuchMethodException {
        MissingRequestHeaderException ex =
                new MissingRequestHeaderException("X-Some-Header", sampleParameter());

        ResponseEntity<ErrorResponse> response = handler.handleMissingHeader(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("MISSING_HEADER", response.getBody().getCode());
        assertTrue(response.getBody().getMessage().contains("X-Some-Header"));
    }
}
