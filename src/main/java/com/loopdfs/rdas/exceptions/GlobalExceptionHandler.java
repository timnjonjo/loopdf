// exception/GlobalExceptionHandler.java
package com.loopdfs.rdas.exceptions;

import com.loopdfs.rdas.dtos.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            ResourceNotFoundException ex, HttpServletRequest req) {

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildError(HttpStatus.NOT_FOUND, ex.getMessage(), req.getRequestURI(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest req) {

        List<ErrorResponse.FieldViolation> violations = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> ErrorResponse.FieldViolation.builder()
                        .field(fe.getField())
                        .message(fe.getDefaultMessage())
                        .build())
                .toList();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildError(HttpStatus.BAD_REQUEST, "Validation failed", req.getRequestURI(), violations));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraint(
            ConstraintViolationException ex, HttpServletRequest req) {

        List<ErrorResponse.FieldViolation> violations = ex.getConstraintViolations()
                .stream()
                .map(cv -> ErrorResponse.FieldViolation.builder()
                        .field(cv.getPropertyPath().toString())
                        .message(cv.getMessage())
                        .build())
                .toList();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildError(HttpStatus.BAD_REQUEST, "Constraint violation", req.getRequestURI(), violations));
    }

    @ExceptionHandler(SoapGatewayException.class)
    public ResponseEntity<ErrorResponse> handleSoapGateway(
            SoapGatewayException ex, HttpServletRequest req) {

        log.error("SOAP gateway error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "3600")
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildError(HttpStatus.SERVICE_UNAVAILABLE,
                        "Reference data service temporarily unavailable. Please retry later.",
                        req.getRequestURI(), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(
            Exception ex, HttpServletRequest req) {

        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildError(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.",
                        req.getRequestURI(), null));
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private ErrorResponse buildError(HttpStatus status, String message,
                                     String path, List<ErrorResponse.FieldViolation> violations) {
        return ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(path)
                .violations(violations)
                .build();
    }
}