// dto/ErrorResponse.java
package com.loopdfs.rdas.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;

/** Uniform error envelope. */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    Instant timestamp;
    int status;
    String error;
    String message;
    String path;
    List<FieldViolation> violations;  // populated for 400 validation errors

    @Value
    @Builder
    public static class FieldViolation {
        String field;
        String message;
    }
}