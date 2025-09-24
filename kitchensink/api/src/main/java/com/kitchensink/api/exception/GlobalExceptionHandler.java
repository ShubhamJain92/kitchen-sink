package com.kitchensink.api.exception;


import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DuplicateKeyException;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Character.toUpperCase;
import static java.util.Optional.ofNullable;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
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
        body.put("instance", req.getRequestURI());
        body.put("timestamp", Instant.now());
        body.put("errors", errors);
        return badRequest().body(body);
    }

    @ExceptionHandler(MemberNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(final MemberNotFoundException ex,
                                                 final ServletWebRequest req) {
        var problemDetail = ProblemDetail.forStatus(NOT_FOUND);
        problemDetail.setTitle("Not Found");
        problemDetail.setDetail(ex.getMessage());
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("path", req.getRequest().getRequestURI());
        return status(NOT_FOUND).body(problemDetail);
    }

    @ExceptionHandler(DuplicateEmailException.class)
    ResponseEntity<ProblemDetail> handleDuplicateEmail(DuplicateEmailException ex,
                                                       ServletWebRequest req) {
        var problemDetail = ProblemDetail.forStatus(CONFLICT);
        problemDetail.setTitle("Duplicate email");
        problemDetail.setDetail("Email already registered.");
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("path", req.getRequest().getRequestURI());
        problemDetail.setProperty("errors", Map.of("email", "Email taken"));
        return status(CONFLICT).body(problemDetail);
    }

    /**
     * Safety net for races: unique index violation from Mongo.
     * Translate to same response as DuplicateEmailException.
     */
    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<ProblemDetail> handleMongoDuplicate(DuplicateKeyException ex,
                                                       ServletWebRequest req) {
        var problemDetail = ProblemDetail.forStatus(CONFLICT);
        problemDetail.setTitle("Duplicate key");
        problemDetail.setDetail("A unique constraint was violated.");
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("path", req.getRequest().getRequestURI());
        final var raw = getRawErrorMessage(ex);
        Map<String, String> errors = extractDupKeyFields(raw);

        if (errors.isEmpty()) {
            errors = Map.of("duplicate", "A record with the same unique field(s) already exists.");
        }

        problemDetail.setProperty("errors", errors);
        return ResponseEntity.status(CONFLICT).body(problemDetail);
    }

    private static String getRawErrorMessage(DuplicateKeyException ex) {
        return Optional.of(ex.getMostSpecificCause())
                .map(Throwable::getMessage)
                .orElseGet(ex::getMessage);
    }

    private static Map<String, String> extractDupKeyFields(String message) {
        final Pattern pattern = Pattern.compile("dup key: \\{\\s*(.+?)\\s*\\}", CASE_INSENSITIVE);
        final Matcher matcher = pattern.matcher(message == null ? "" : message);

        if (!matcher.find()) return Collections.emptyMap();

        String keyPart = matcher.group(1); // e.g. phoneNumber: "+916321457899", email: "a@x.com"
        Map<String, String> map = new LinkedHashMap<>();

        for (String kv : keyPart.split(",\\s*")) {
            String[] parts = kv.split(":\\s*", 2);
            if (parts.length != 2) continue;
            String field = unquote(parts[0]);
            map.put(field, friendlyMessage(field));
        }
        return map;
    }

    private static String unquote(String str) {
        if (str == null) return "";
        str = str.trim();
        if ((str.startsWith("\"") && str.endsWith("\"")) || (str.startsWith("'") && str.endsWith("'"))) {
            return str.substring(1, str.length() - 1);
        }
        return str;
    }

    private static String friendlyMessage(final String field) {
        final var label = switch (field) {
            case "email" -> "Email";
            case "phoneNumber" -> "Phone number";
            default -> capitalize(field);
        };
        return label + " already exists";
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return "";
        return toUpperCase(s.charAt(0)) + s.substring(1);
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ProblemDetail> handleRSE(ResponseStatusException ex,
                                            ServletWebRequest req) {
        var problemDetail = ProblemDetail.forStatus(ex.getStatusCode());
        problemDetail.setTitle(ex.getReason() != null ? ex.getReason() : "Error");
        problemDetail.setDetail(ex.getMessage());
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("path", req.getRequest().getRequestURI());
        return status(ex.getStatusCode()).body(problemDetail);
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
