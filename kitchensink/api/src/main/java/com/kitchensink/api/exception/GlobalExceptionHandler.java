package com.kitchensink.api.exception;


import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.server.ResponseStatusException;

import java.security.SignatureException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;
import static org.springframework.http.HttpStatus.*;
import static org.springframework.http.ResponseEntity.badRequest;
import static org.springframework.http.ResponseEntity.status;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static LinkedHashMap<String, String> getInvalidValueMap(final MethodArgumentNotValidException ex) {
        return ex.getBindingResult().getFieldErrors().stream()
                .collect(toMap(
                        FieldError::getField,
                        fe -> ofNullable(fe.getDefaultMessage()).orElse("Invalid value"),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> handleBeanValidation(final MethodArgumentNotValidException ex,
                                                             final HttpServletRequest req) {
        Map<String, String> errors = getInvalidValueMap(ex);

        var body = new LinkedHashMap<String, Object>();
        body.put("type", "about:blank");
        body.put("title", "Validation failed");
        body.put("status", 400);
        //body.put("detail", "One or more fields are invalid.");
        body.put("instance", req.getRequestURI());
        body.put("timestamp", Instant.now());
        body.put("errors", errors);
        return badRequest().body(body);
    }

    @ExceptionHandler(MemberNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(MemberNotFoundException ex,
                                                 ServletWebRequest req) {
        var pd = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        pd.setTitle("Not Found");
        pd.setDetail(ex.getMessage());
        pd.setProperty("timestamp", Instant.now());
        pd.setProperty("path", req.getRequest().getRequestURI());
        return status(HttpStatus.NOT_FOUND).body(pd);
    }

    @ExceptionHandler(DuplicateEmailException.class)
    ResponseEntity<ProblemDetail> handleDuplicateEmail(DuplicateEmailException ex,
                                                       ServletWebRequest req) {
        var pd = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        pd.setTitle("Duplicate email");
        pd.setDetail("Email already registered.");
        pd.setProperty("timestamp", Instant.now());
        pd.setProperty("path", req.getRequest().getRequestURI());
        pd.setProperty("errors", Map.of("email", "Email taken"));
        return status(HttpStatus.CONFLICT).body(pd);
    }

    /**
     * Safety net for races: unique index violation from Mongo.
     * Translate to same response as DuplicateEmailException.
     */
    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<ProblemDetail> handleMongoDuplicate(DuplicateKeyException ex,
                                                       ServletWebRequest req) {
        var pd = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        pd.setTitle("Duplicate key");
        pd.setDetail("A unique constraint was violated.");
        pd.setProperty("timestamp", Instant.now());
        pd.setProperty("path", req.getRequest().getRequestURI());
        // Best-effort field hint
        pd.setProperty("errors", Map.of("email", "Email taken"));
        return status(HttpStatus.CONFLICT).body(pd);
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ProblemDetail> handleRSE(ResponseStatusException ex,
                                            ServletWebRequest req) {
        var pd = ProblemDetail.forStatus(ex.getStatusCode());
        pd.setTitle(ex.getReason() != null ? ex.getReason() : "Error");
        pd.setDetail(ex.getMessage());
        pd.setProperty("timestamp", Instant.now());
        pd.setProperty("path", req.getRequest().getRequestURI());
        return status(ex.getStatusCode()).body(pd);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleGeneric(final Exception ex, final ServletWebRequest req) {
        ProblemDetail errorDetail;
        if (ex instanceof BadCredentialsException) {
            errorDetail = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(401), ex.getMessage());
            errorDetail.setProperty("access_denied_reason", "Authentication failure");
            return status(UNAUTHORIZED).body(errorDetail);
        }

        if (ex instanceof AccessDeniedException) {
            errorDetail = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(403), ex.getMessage());
            errorDetail.setProperty("access_denied_reason", "Not Authorized");
            return status(FORBIDDEN).body(errorDetail);
        }

        if (ex instanceof SignatureException) {
            errorDetail = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(403), ex.getMessage());
            errorDetail.setProperty("access_denied_reason", "JWT Signature not valid");
            return status(FORBIDDEN).body(errorDetail);
        }

        if (ex instanceof ExpiredJwtException) {
            errorDetail = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(403), ex.getMessage());
            errorDetail.setProperty("access_denied_reason", "JWT token already expired");
            return status(FORBIDDEN).body(errorDetail);
        }

        var problemDetail = ProblemDetail.forStatus(INTERNAL_SERVER_ERROR);
        problemDetail.setTitle("Internal error");
        problemDetail.setDetail("Something went wrong.");
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("path", req.getRequest().getRequestURI());
        return status(INTERNAL_SERVER_ERROR).body(problemDetail);
    }
}

