package com.logstream.exception;

import java.time.OffsetDateTime;
import java.util.stream.Collectors;

import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import com.logstream.generated.model.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final String INGESTION_TOKEN_HEADER = "X-Ingestion-Token";

    @ExceptionHandler(QuotaExceededException.class)
    public ResponseEntity<ErrorResponse> handleQuota(QuotaExceededException ex, HttpServletRequest request) {
        return error(HttpStatus.TOO_MANY_REQUESTS, "QUOTA_EXCEEDED", ex.getMessage(), request);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(RateLimitExceededException ex, HttpServletRequest request) {
        return error(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED", ex.getMessage(), request);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException ex, HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage(), request);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex, HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", ex.getMessage(), request);
    }

    @ExceptionHandler(KafkaUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleKafkaUnavailable(KafkaUnavailableException ex, HttpServletRequest request) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "KAFKA_UNAVAILABLE", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidLogEventException.class)
    public ResponseEntity<ErrorResponse> handleInvalidLogEvent(InvalidLogEventException ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_LOG_EVENT", ex.getMessage(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "MALFORMED_JSON",
                "Request body is not valid JSON or does not match the expected structure.", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));
        if (details.isBlank()) {
            details = "Request validation failed.";
        }
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", details, request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerMethodValidation(HandlerMethodValidationException ex,
            HttpServletRequest request) {
        String details = ex.getAllErrors().stream()
                .map(ApiExceptionHandler::describe)
                .collect(Collectors.joining("; "));
        if (details.isBlank()) {
            details = "Request validation failed.";
        }
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", details, request);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex,
            HttpServletRequest request) {
        if (INGESTION_TOKEN_HEADER.equalsIgnoreCase(ex.getHeaderName())) {
            return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Missing ingestion token", request);
        }
        return error(HttpStatus.BAD_REQUEST, "MISSING_HEADER",
                "Required header " + ex.getHeaderName() + " is missing.", request);
    }

    private static String describe(MessageSourceResolvable resolvable) {
        if (resolvable instanceof FieldError fieldError) {
            return fieldError.getField() + ": " + fieldError.getDefaultMessage();
        }
        String message = resolvable.getDefaultMessage();
        return message == null ? "invalid value" : message;
    }

    public static ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message, HttpServletRequest request) {
        ErrorResponse response = new ErrorResponse(status.value(), message, OffsetDateTime.now());
        response.setError(status.getReasonPhrase());
        response.setPath(request.getRequestURI());
        response.setCode(code);
        return ResponseEntity.status(status).body(response);
    }
}
